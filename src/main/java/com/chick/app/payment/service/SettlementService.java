package com.chick.app.payment.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.domain.BranchSettlementAllocation;
import com.chick.app.payment.dto.BalanceResponse;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BranchSettlementResponse;
import com.chick.app.payment.dto.SettlementReverseRequest;
import com.chick.app.payment.mapper.SettlementMapper;
import com.chick.app.payment.repository.BranchSettlementRepository;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

	private static final int MONEY_SCALE = 2;

	private final BranchSettlementRepository settlements;
	private final SettlementMapper mapper;
	private final BranchRepository branches;
	private final BranchSaleRepository branchSales;
	private final BranchAccessService branchAccess;

	public SettlementService(
			BranchSettlementRepository settlements,
			SettlementMapper mapper,
			BranchRepository branches,
			BranchSaleRepository branchSales,
			BranchAccessService branchAccess) {
		this.settlements = settlements;
		this.mapper = mapper;
		this.branches = branches;
		this.branchSales = branchSales;
		this.branchAccess = branchAccess;
	}

	@Transactional(readOnly = true)
	public Page<BranchSettlementResponse> list(Pageable pageable, UUID branchId) {
		if (branchId != null) {
			branchAccess.require(branchId);
		}
		Specification<BranchSettlement> specification = branchAccess.restrict("branch");
		if (branchId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		return settlements.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public BranchSettlementResponse get(UUID id) {
		BranchSettlement settlement = find(id);
		branchAccess.require(settlement.getBranch().getId());
		return mapper.toResponse(settlement);
	}

	@Transactional
	@Audited(action = "SETTLEMENT_CREATE", entityType = "BranchSettlement")
	public BranchSettlementResponse create(BranchSettlementCreateRequest request) {
		branchAccess.require(request.branchId());
		Branch branch = branches.lockById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		Map<UUID, BigDecimal> requestedBySale = new LinkedHashMap<>();
		BigDecimal allocationTotal = BigDecimal.ZERO;
		for (var allocationRequest : request.allocations()) {
			BigDecimal amount = allocationRequest.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			requestedBySale.merge(allocationRequest.branchSaleId(), amount, BigDecimal::add);
			allocationTotal = allocationTotal.add(amount);
		}
		BigDecimal settlementAmount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		if (allocationTotal.compareTo(settlementAmount) != 0) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Settlement allocations must sum to the settlement amount", HttpStatus.BAD_REQUEST);
		}

		Map<UUID, BranchSale> requestedSales = new LinkedHashMap<>();
		for (var entry : requestedBySale.entrySet()) {
			BranchSale sale = branchSales.findById(entry.getKey())
					.orElseThrow(() -> new ResourceNotFoundException("BranchSale", entry.getKey()));
			if (!sale.getBranch().getId().equals(branch.getId())) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Branch sale does not belong to branch", HttpStatus.BAD_REQUEST);
			}
			BigDecimal outstanding = saleOutstanding(sale);
			if (entry.getValue().compareTo(outstanding) > 0) {
				throw new BusinessException("OVER_ALLOCATION",
						"Settlement allocation exceeds the outstanding balance of the branch sale",
						HttpStatus.CONFLICT);
			}
			requestedSales.put(sale.getId(), sale);
		}

		BranchSettlement settlement = mapper.toEntity(request);
		settlement.setBranch(branch);
		settlement.setAmount(settlementAmount);
		settlement.setStatus(BranchSettlement.Status.POSTED);

		for (var allocationRequest : request.allocations()) {
			BranchSale sale = requestedSales.get(allocationRequest.branchSaleId());

			BranchSettlementAllocation allocation = mapper.toAllocation(allocationRequest);
			allocation.setBranchSettlement(settlement);
			allocation.setBranchSale(sale);
			allocation.setAmount(allocationRequest.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			settlement.getAllocations().add(allocation);
		}

		return mapper.toResponse(settlements.save(settlement));
	}

	@Transactional
	@Audited(action = "SETTLEMENT_REVERSE", entityType = "BranchSettlement")
	public BranchSettlementResponse reverse(UUID id, SettlementReverseRequest request) {
		BranchSettlement settlement = find(id);
		branchAccess.require(settlement.getBranch().getId());
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
		branchAccess.require(branchId);
		branches.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		BigDecimal transferValue = branchSales.totalTransferValue(branchId);
		BigDecimal settled = settlements.allocatedForBranch(branchId, BranchSettlement.Status.POSTED);
		return new BalanceResponse(transferValue.subtract(settled).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
	}

	/** Unpaid branch sales, oldest first, so a payment can be allocated to them in order. */
	@Transactional(readOnly = true)
	public java.util.List<com.chick.app.payment.dto.OpenSaleResponse> openSales(UUID branchId) {
		branchAccess.require(branchId);
		branches.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		Map<UUID, BigDecimal> paid = new java.util.HashMap<>();
		for (Object[] row : settlements.allocatedBySale(branchId, BranchSettlement.Status.POSTED)) {
			paid.put((UUID) row[0], (BigDecimal) row[1]);
		}
		return branchSales.findByBranchIdOrderBySaleDateAsc(branchId).stream()
				.map(sale -> new com.chick.app.payment.dto.OpenSaleResponse(
						sale.getId(),
						sale.getReference(),
						sale.getSaleDate(),
						sale.getTransferValue(),
						sale.getTransferValue().subtract(paid.getOrDefault(sale.getId(), BigDecimal.ZERO))
								.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP)))
				.filter(open -> open.outstandingAmount().signum() > 0)
				.toList();
	}

	/** Amount of posted settlements allocated to a branch sale. */
	@Transactional(readOnly = true)
	public BigDecimal settledAmount(UUID branchSaleId) {
		return settlements.allocatedToSale(branchSaleId, BranchSettlement.Status.POSTED);
	}

	private BranchSettlement find(UUID id) {
		return settlements.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("BranchSettlement", id));
	}

	private BigDecimal saleOutstanding(BranchSale sale) {
		return sale.getTransferValue().subtract(settledAmount(sale.getId())).max(BigDecimal.ZERO);
	}
}
