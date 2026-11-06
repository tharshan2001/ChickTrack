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
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.payment.dto.OpenDeliveryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branch payments. A branch owes us for every delivery it receives (received weight x transfer price);
 * a settlement pays those deliveries, oldest first by default.
 */
@Service
public class SettlementService {

	private static final int MONEY_SCALE = 2;

	private static final EnumSet<DistributionStatus> RECEIVED_STATUSES =
			EnumSet.of(DistributionStatus.RECEIVED, DistributionStatus.CLOSED);

	private final BranchSettlementRepository settlements;
	private final SettlementMapper mapper;
	private final BranchRepository branches;
	private final DistributionRepository distributions;
	private final BranchReceiptRepository receipts;
	private final BranchAccessService branchAccess;

	public SettlementService(
			BranchSettlementRepository settlements,
			SettlementMapper mapper,
			BranchRepository branches,
			DistributionRepository distributions,
			BranchReceiptRepository receipts,
			BranchAccessService branchAccess) {
		this.settlements = settlements;
		this.mapper = mapper;
		this.branches = branches;
		this.distributions = distributions;
		this.receipts = receipts;
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

		Map<UUID, BigDecimal> requestedByDelivery = new LinkedHashMap<>();
		BigDecimal allocationTotal = BigDecimal.ZERO;
		for (var allocationRequest : request.allocations()) {
			BigDecimal amount = allocationRequest.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			requestedByDelivery.merge(allocationRequest.distributionId(), amount, BigDecimal::add);
			allocationTotal = allocationTotal.add(amount);
		}
		BigDecimal settlementAmount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		if (allocationTotal.compareTo(settlementAmount) != 0) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Settlement allocations must sum to the settlement amount", HttpStatus.BAD_REQUEST);
		}

		Map<UUID, Distribution> requestedDeliveries = new LinkedHashMap<>();
		for (var entry : requestedByDelivery.entrySet()) {
			Distribution delivery = distributions.findById(entry.getKey())
					.orElseThrow(() -> new ResourceNotFoundException("Distribution", entry.getKey()));
			if (!delivery.getBranch().getId().equals(branch.getId())) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Delivery does not belong to branch", HttpStatus.BAD_REQUEST);
			}
			if (!RECEIVED_STATUSES.contains(delivery.getStatus())) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Only received deliveries can be paid", HttpStatus.CONFLICT);
			}
			BigDecimal outstanding = deliveryOutstanding(delivery.getId());
			if (entry.getValue().compareTo(outstanding) > 0) {
				throw new BusinessException("OVER_ALLOCATION",
						"Settlement allocation exceeds the outstanding balance of delivery " + delivery.getReference(),
						HttpStatus.CONFLICT);
			}
			requestedDeliveries.put(delivery.getId(), delivery);
		}

		BranchSettlement settlement = mapper.toEntity(request);
		settlement.setBranch(branch);
		settlement.setAmount(settlementAmount);
		settlement.setStatus(BranchSettlement.Status.POSTED);

		for (var allocationRequest : request.allocations()) {
			BranchSettlementAllocation allocation = mapper.toAllocation(allocationRequest);
			allocation.setBranchSettlement(settlement);
			allocation.setDistribution(requestedDeliveries.get(allocationRequest.distributionId()));
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

	/** What the branch owes: every received delivery's value minus posted settlements. */
	@Transactional(readOnly = true)
	public BalanceResponse branchBalance(UUID branchId) {
		branchAccess.require(branchId);
		branches.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		return new BalanceResponse(outstanding(branchId));
	}

	/** Received value of a branch's deliveries minus posted settlements (no access check; for reports). */
	@Transactional(readOnly = true)
	public BigDecimal outstanding(UUID branchId) {
		BigDecimal owed = BigDecimal.ZERO;
		for (Object[] row : receipts.receivedValueByDistribution(branchId, RECEIVED_STATUSES)) {
			owed = owed.add(money((BigDecimal) row[3]));
		}
		BigDecimal settled = settlements.allocatedForBranch(branchId, BranchSettlement.Status.POSTED);
		return owed.subtract(settled).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	/** Received deliveries the branch has not fully paid for, oldest first. */
	@Transactional(readOnly = true)
	public List<OpenDeliveryResponse> openDeliveries(UUID branchId) {
		branchAccess.require(branchId);
		branches.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		Map<UUID, BigDecimal> paid = new HashMap<>();
		for (Object[] row : settlements.allocatedByDistribution(branchId, BranchSettlement.Status.POSTED)) {
			if (row[0] != null) {
				paid.put((UUID) row[0], (BigDecimal) row[1]);
			}
		}
		return receipts.receivedValueByDistribution(branchId, RECEIVED_STATUSES).stream()
				.map(row -> {
					BigDecimal owed = money((BigDecimal) row[3]);
					return new OpenDeliveryResponse(
							(UUID) row[0],
							(String) row[1],
							(Instant) row[2],
							owed,
							owed.subtract(paid.getOrDefault((UUID) row[0], BigDecimal.ZERO))
									.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				})
				.filter(open -> open.outstandingAmount().signum() > 0)
				.toList();
	}

	/** Amount of posted settlements allocated to a delivery. */
	@Transactional(readOnly = true)
	public BigDecimal settledOnDistribution(UUID distributionId) {
		return settlements.allocatedToDistribution(distributionId, BranchSettlement.Status.POSTED);
	}

	private BranchSettlement find(UUID id) {
		return settlements.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("BranchSettlement", id));
	}

	private BigDecimal deliveryOutstanding(UUID distributionId) {
		return money(receipts.receivedValue(distributionId))
				.subtract(settledOnDistribution(distributionId)).max(BigDecimal.ZERO);
	}

	private static BigDecimal money(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}
}
