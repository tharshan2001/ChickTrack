package com.chick.app.payment.service;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.domain.BranchSettlementAllocation;
import com.chick.app.payment.domain.Payment;
import com.chick.app.payment.domain.PaymentAllocation;
import com.chick.app.payment.dto.BalanceResponse;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BranchSettlementResponse;
import com.chick.app.payment.dto.PaymentCreateRequest;
import com.chick.app.payment.dto.PaymentResponse;
import com.chick.app.payment.dto.PaymentReverseRequest;
import com.chick.app.payment.dto.PaymentReverseRequest;
import com.chick.app.payment.mapper.PaymentMapper;
import com.chick.app.payment.repository.BranchSettlementRepository;
import com.chick.app.payment.repository.PaymentRepository;
import com.chick.app.procurement.domain.Procurement;
import com.chick.app.procurement.domain.ProcurementStatus;
import com.chick.app.procurement.repository.ProcurementRepository;
import com.chick.app.supplier.domain.Supplier;
import com.chick.app.supplier.repository.SupplierRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

	private static final int MONEY_SCALE = 2;

	private final PaymentRepository payments;
	private final BranchSettlementRepository settlements;
	private final PaymentMapper mapper;
	private final SupplierRepository suppliers;
	private final ProcurementRepository procurements;
	private final BranchRepository branches;
	private final DistributionRepository distributions;

	public PaymentService(
			PaymentRepository payments,
			BranchSettlementRepository settlements,
			PaymentMapper mapper,
			SupplierRepository suppliers,
			ProcurementRepository procurements,
			BranchRepository branches,
			DistributionRepository distributions) {
		this.payments = payments;
		this.settlements = settlements;
		this.mapper = mapper;
		this.suppliers = suppliers;
		this.procurements = procurements;
		this.branches = branches;
		this.distributions = distributions;
	}

	@Transactional(readOnly = true)
	public Page<PaymentResponse> listPayments(Pageable pageable, UUID supplierId) {
		Specification<Payment> specification = (root, query, cb) -> cb.conjunction();
		if (supplierId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
		}
		return payments.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public PaymentResponse getPayment(UUID id) {
		return mapper.toResponse(findPayment(id));
	}

	@Transactional
	public PaymentResponse createPayment(PaymentCreateRequest request) {
		Supplier supplier = suppliers.findById(request.supplierId())
				.orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

		BigDecimal allocationTotal = BigDecimal.ZERO;
		for (var allocationRequest : request.allocations()) {
			allocationTotal = allocationTotal.add(allocationRequest.amount());
		}
		if (allocationTotal.compareTo(request.amount()) != 0) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Payment allocations must sum to the payment amount", HttpStatus.BAD_REQUEST);
		}

		Payment payment = mapper.toEntity(request);
		payment.setSupplier(supplier);
		payment.setAmount(request.amount().setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP));
		payment.setStatus(Payment.Status.POSTED);

		for (var allocationRequest : request.allocations()) {
			Procurement procurement = procurements.findById(allocationRequest.procurementId())
					.orElseThrow(() -> new ResourceNotFoundException("Procurement", allocationRequest.procurementId()));
			if (!procurement.getSupplier().getId().equals(supplier.getId())) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Procurement does not belong to supplier", HttpStatus.BAD_REQUEST);
			}
			if (procurement.getStatus() == ProcurementStatus.DRAFT || procurement.getStatus() == ProcurementStatus.REVERSED) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Cannot allocate payment to this procurement", HttpStatus.BAD_REQUEST);
			}

			BigDecimal outstanding = procurementOutstanding(procurement);
			if (allocationRequest.amount().compareTo(outstanding) > 0) {
				throw new BusinessException("OVER_ALLOCATION",
						"Payment allocation exceeds outstanding balance", HttpStatus.CONFLICT);
			}

			PaymentAllocation allocation = mapper.toAllocation(allocationRequest);
			allocation.setPayment(payment);
			allocation.setProcurement(procurement);
			allocation.setAmount(allocationRequest.amount().setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP));
			payment.getAllocations().add(allocation);
		}

		return mapper.toResponse(payments.save(payment));
	}

	@Transactional
	public PaymentResponse reversePayment(UUID id, PaymentReverseRequest request) {
		Payment payment = findPayment(id);
		if (payment.getStatus() != Payment.Status.POSTED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Payment cannot be reversed in its current status", HttpStatus.CONFLICT);
		}
		payment.setStatus(Payment.Status.REVERSED);
		payment.setReversalReason(request.reason());
		return mapper.toResponse(payments.save(payment));
	}

	@Transactional(readOnly = true)
	public BalanceResponse supplierBalance(UUID supplierId) {
		suppliers.findById(supplierId)
				.orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));

		BigDecimal totalCost = procurements.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("supplier").get("id"), supplierId),
				cb.notEqual(root.get("status"), ProcurementStatus.DRAFT),
				cb.notEqual(root.get("status"), ProcurementStatus.REVERSED)))
				.stream()
				.map(Procurement::getTotalCost)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal paid = payments.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("supplier").get("id"), supplierId),
				cb.equal(root.get("status"), Payment.Status.POSTED)))
				.stream()
				.flatMap(payment -> payment.getAllocations().stream())
				.map(PaymentAllocation::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new BalanceResponse(totalCost.subtract(paid).setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP));
	}

	@Transactional(readOnly = true)
	public Page<BranchSettlementResponse> listSettlements(Pageable pageable, UUID branchId) {
		Specification<BranchSettlement> specification = (root, query, cb) -> cb.conjunction();
		if (branchId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		return settlements.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public BranchSettlementResponse getSettlement(UUID id) {
		return mapper.toResponse(findSettlement(id));
	}

	@Transactional
	public BranchSettlementResponse createSettlement(BranchSettlementCreateRequest request) {
		Branch branch = branches.findById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		BigDecimal allocationTotal = BigDecimal.ZERO;
		for (var allocationRequest : request.allocations()) {
			allocationTotal = allocationTotal.add(allocationRequest.amount());
		}
		if (allocationTotal.compareTo(request.amount()) != 0) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Settlement allocations must sum to the settlement amount", HttpStatus.BAD_REQUEST);
		}

		BranchSettlement settlement = mapper.toEntity(request);
		settlement.setBranch(branch);
		settlement.setAmount(request.amount().setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP));
		settlement.setStatus(BranchSettlement.Status.POSTED);

		for (var allocationRequest : request.allocations()) {
			Distribution distribution = distributions.findById(allocationRequest.distributionId())
					.orElseThrow(() -> new ResourceNotFoundException("Distribution", allocationRequest.distributionId()));
			if (!distribution.getBranch().getId().equals(branch.getId())) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Distribution does not belong to branch", HttpStatus.BAD_REQUEST);
			}
			if (distribution.getStatus() == DistributionStatus.DRAFT || distribution.getStatus() == DistributionStatus.REVERSED) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Cannot allocate settlement to this distribution", HttpStatus.BAD_REQUEST);
			}

			BigDecimal outstanding = distributionOutstanding(distribution);
			if (allocationRequest.amount().compareTo(outstanding) > 0) {
				throw new BusinessException("OVER_ALLOCATION",
						"Settlement allocation exceeds outstanding balance", HttpStatus.CONFLICT);
			}

			BranchSettlementAllocation allocation = mapper.toAllocation(allocationRequest);
			allocation.setBranchSettlement(settlement);
			allocation.setDistribution(distribution);
			allocation.setAmount(allocationRequest.amount().setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP));
			settlement.getAllocations().add(allocation);
		}

		return mapper.toResponse(settlements.save(settlement));
	}

	@Transactional
	public BranchSettlementResponse reverseSettlement(UUID id, PaymentReverseRequest request) {
		BranchSettlement settlement = findSettlement(id);
		if (settlement.getStatus() != BranchSettlement.Status.POSTED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Settlement cannot be reversed in its current status", HttpStatus.CONFLICT);
		}
		settlement.setStatus(BranchSettlement.Status.REVERSED);
		settlement.setReversalReason(request.reason());
		return mapper.toResponse(settlements.save(settlement));
	}

	@Transactional(readOnly = true)
	public BalanceResponse branchBalance(UUID branchId) {
		branches.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));

		BigDecimal totalRevenue = distributions.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("branch").get("id"), branchId),
				cb.notEqual(root.get("status"), DistributionStatus.DRAFT),
				cb.notEqual(root.get("status"), DistributionStatus.REVERSED)))
				.stream()
				.map(Distribution::getTotalRevenue)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal settled = settlements.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("branch").get("id"), branchId),
				cb.equal(root.get("status"), BranchSettlement.Status.POSTED)))
				.stream()
				.flatMap(settlement -> settlement.getAllocations().stream())
				.map(BranchSettlementAllocation::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new BalanceResponse(totalRevenue.subtract(settled).setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP));
	}

	private Payment findPayment(UUID id) {
		return payments.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Payment", id));
	}

	private BranchSettlement findSettlement(UUID id) {
		return settlements.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("BranchSettlement", id));
	}

	private BigDecimal procurementOutstanding(Procurement procurement) {
		BigDecimal paid = payments.findAll((root, query, cb) -> cb.equal(root.get("status"), Payment.Status.POSTED))
				.stream()
				.flatMap(payment -> payment.getAllocations().stream())
				.filter(allocation -> allocation.getProcurement().getId().equals(procurement.getId()))
				.map(PaymentAllocation::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return procurement.getTotalCost().subtract(paid);
	}

	private BigDecimal distributionOutstanding(Distribution distribution) {
		BigDecimal settled = settlements.findAll((root, query, cb) -> cb.equal(root.get("status"), BranchSettlement.Status.POSTED))
				.stream()
				.flatMap(settlement -> settlement.getAllocations().stream())
				.filter(allocation -> allocation.getDistribution().getId().equals(distribution.getId()))
				.map(BranchSettlementAllocation::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return distribution.getTotalRevenue().subtract(settled);
	}
}
