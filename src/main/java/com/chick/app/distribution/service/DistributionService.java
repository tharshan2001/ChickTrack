package com.chick.app.distribution.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.domain.BranchReceipt;
import com.chick.app.distribution.domain.BranchReceiptItem;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionExpense;
import com.chick.app.distribution.domain.DistributionItem;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.domain.DiscrepancyReason;
import com.chick.app.distribution.domain.ExpenseAllocation;
import com.chick.app.distribution.dto.BranchReceiptRequest;
import com.chick.app.distribution.dto.DistributionCreateRequest;
import com.chick.app.distribution.dto.DistributionExpenseRequest;
import com.chick.app.distribution.dto.DistributionItemRequest;
import com.chick.app.distribution.dto.DistributionResponse;
import com.chick.app.distribution.dto.BranchReceiptResponse;
import com.chick.app.distribution.dto.DistributionReverseRequest;
import com.chick.app.distribution.mapper.DistributionMapper;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.ExpenseAllocationRepository;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.expense.domain.ExpenseCategory;
import com.chick.app.expense.repository.ExpenseCategoryRepository;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.margin.service.CompanyMarginRuleService;
import com.chick.app.sales.service.BranchSaleService;
import java.util.HashSet;
import java.util.Set;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import com.chick.app.common.time.BusinessTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

@Service
public class DistributionService {

	private static final int MONEY_SCALE = 2;
	private static final int COST_SCALE = 4;
	private static final int WEIGHT_SCALE = 3;

	private final DistributionRepository distributions;
	private final DistributionMapper mapper;
	private final BranchRepository branches;
	private final ChickenTypeRepository chickenTypes;
	private final ExpenseCategoryRepository expenseCategories;
	private final StockBatchRepository stockBatches;
	private final InventoryMovementRepository movements;
	private final BranchReceiptRepository branchReceipts;
	private final ExpenseAllocationRepository expenseAllocations;
	private final DocumentNumberService documentNumbers;
	private final CompanyMarginRuleService marginRules;
	private final BranchAccessService branchAccess;
	private final BranchSaleService branchSales;

	public DistributionService(
			DistributionRepository distributions,
			DistributionMapper mapper,
			BranchRepository branches,
			ChickenTypeRepository chickenTypes,
			ExpenseCategoryRepository expenseCategories,
			StockBatchRepository stockBatches,
			InventoryMovementRepository movements,
			BranchReceiptRepository branchReceipts,
			ExpenseAllocationRepository expenseAllocations,
			DocumentNumberService documentNumbers,
			CompanyMarginRuleService marginRules,
			BranchAccessService branchAccess,
			BranchSaleService branchSales) {
		this.distributions = distributions;
		this.mapper = mapper;
		this.branches = branches;
		this.chickenTypes = chickenTypes;
		this.expenseCategories = expenseCategories;
		this.stockBatches = stockBatches;
		this.movements = movements;
		this.branchReceipts = branchReceipts;
		this.expenseAllocations = expenseAllocations;
		this.documentNumbers = documentNumbers;
		this.marginRules = marginRules;
		this.branchAccess = branchAccess;
		this.branchSales = branchSales;
	}

	@Transactional(readOnly = true)
	public Page<DistributionResponse> list(Pageable pageable, UUID branchId, DistributionStatus status, LocalDate from, LocalDate to) {
		if (branchId != null) {
			branchAccess.require(branchId);
		}
		Specification<Distribution> specification = branchAccess.restrict("branch");
		if (branchId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		if (status != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
		}
		if (from != null) {
			specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("distributionDate"), from));
		}
		if (to != null) {
			specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("distributionDate"), to));
		}
		return distributions.findAll(specification, pageable).map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public DistributionResponse get(UUID id) {
		return toResponse(findAccessible(id));
	}

	@Transactional
	@Audited(action = "DISTRIBUTION_CREATE", entityType = "Distribution")
	public DistributionResponse create(DistributionCreateRequest request) {
		branchAccess.require(request.branchId());
		Branch branch = branches.findById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		Distribution distribution = mapper.toEntity(request);
		distribution.setBranch(branch);
		distribution.setStatus(DistributionStatus.DRAFT);
		distribution.setReference(documentNumbers.next("DISTRIBUTION", "DST"));

		// Allocate FIFO from unreserved stock and reserve it, so two drafts can never promise the same weight.
		// Remaining weight is only reduced on dispatch.
		Map<UUID, BigDecimal> availableByBatch = new HashMap<>();
		List<AllocatedItem> allocations = new ArrayList<>();
		for (DistributionItemRequest itemRequest : request.items()) {
			ChickenType chickenType = chickenTypes.findById(itemRequest.chickenTypeId())
					.orElseThrow(() -> new ResourceNotFoundException("ChickenType", itemRequest.chickenTypeId()));
			List<StockBatch> batches = stockBatches.findAvailableByChickenType(chickenType);
			BigDecimal remainingToAllocate = itemRequest.weightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
			BigDecimal available = batches.stream()
					.map(batch -> availableByBatch.computeIfAbsent(batch.getId(), id -> batch.availableWeightKg()))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			if (available.compareTo(remainingToAllocate) < 0) {
				throw new BusinessException("INSUFFICIENT_STOCK",
						"Insufficient stock for chicken type " + chickenType.getId(), HttpStatus.CONFLICT);
			}

			BigDecimal allocatedWeight = BigDecimal.ZERO;
			for (StockBatch batch : batches) {
				if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
					break;
				}
				BigDecimal batchAvailable = availableByBatch.computeIfAbsent(
						batch.getId(), id -> batch.availableWeightKg());
				BigDecimal allocate = batchAvailable.min(remainingToAllocate);
				if (allocate.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				allocations.add(new AllocatedItem(itemRequest, chickenType, batch, allocate));
				availableByBatch.put(batch.getId(), batchAvailable.subtract(allocate));
				remainingToAllocate = remainingToAllocate.subtract(allocate);
				allocatedWeight = allocatedWeight.add(allocate);
			}
			if (remainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
				throw new BusinessException("INSUFFICIENT_STOCK",
						"Unable to allocate stock for chicken type " + chickenType.getId(), HttpStatus.CONFLICT);
			}
		}

		for (AllocatedItem allocation : allocations) {
			StockBatch batch = allocation.batch;
			batch.setReservedWeightKg(batch.getReservedWeightKg().add(allocation.weight));
			stockBatches.save(batch);
			DistributionItem item = new DistributionItem();
			item.setDistribution(distribution);
			item.setStockBatch(batch);
			item.setChickenType(allocation.chickenType);
			item.setWeightKg(allocation.weight);
			item.setUnitCost(batch.getLandedCostPerKg());
			BigDecimal markupPercent = marginRules.markupPercent(
					allocation.chickenType.getId(),
					branch.getId(),
					endOfDay(request.distributionDate()));
			BigDecimal ourMarginPerKg = batch.getLandedCostPerKg()
					.multiply(markupPercent)
					.divide(BigDecimal.valueOf(100), COST_SCALE, RoundingMode.HALF_UP);
			item.setOurMarginPerKg(ourMarginPerKg);
			item.setDistributionExpensePerKg(BigDecimal.ZERO.setScale(COST_SCALE));
			item.setTransferPricePerKg(batch.getLandedCostPerKg().add(ourMarginPerKg));
			item.setLineCost(item.getWeightKg().multiply(item.getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setLineTransferValue(item.getWeightKg()
					.multiply(item.getTransferPricePerKg())
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			distribution.getItems().add(item);
		}

		if (request.expenses() != null) {
			for (DistributionExpenseRequest expenseRequest : request.expenses()) {
				ExpenseCategory category = null;
				if (expenseRequest.categoryId() != null) {
					category = expenseCategories.findById(expenseRequest.categoryId())
							.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", expenseRequest.categoryId()));
				}
				DistributionExpense expense = mapper.toExpense(expenseRequest);
				expense.setDistribution(distribution);
				expense.setCategory(category);
				expense.setAmount(expenseRequest.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				if (expenseRequest.allocationMethod() != null) {
					expense.setAllocationMethod(expenseRequest.allocationMethod());
				}
				distribution.getExpenses().add(expense);
			}
		}

		distributions.save(distribution);
		allocateExpenses(distribution);
		recalculate(distribution);
		distributions.save(distribution);
		return toResponse(distribution);
	}

	@Transactional
	@Audited(action = "DISTRIBUTION_DISPATCH", entityType = "Distribution")
	public DistributionResponse dispatch(UUID id) {
		Distribution distribution = findAccessible(id);
		if (distribution.getStatus() != DistributionStatus.DRAFT) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Only draft distributions can be dispatched", HttpStatus.CONFLICT);
		}

		// Validate the whole distribution before mutating any batch, so a partial dispatch cannot occur.
		Map<UUID, StockBatch> batchesById = new LinkedHashMap<>();
		Map<UUID, BigDecimal> weightByBatch = new LinkedHashMap<>();
		for (DistributionItem item : distribution.getItems()) {
			StockBatch batch = item.getStockBatch();
			batchesById.putIfAbsent(batch.getId(), batch);
			weightByBatch.merge(batch.getId(), item.getWeightKg(), BigDecimal::add);
		}
		// Lock the batches before checking stock so two dispatches cannot both pass the check.
		for (StockBatch locked : stockBatches.lockAllById(weightByBatch.keySet())) {
			batchesById.put(locked.getId(), locked);
		}
		for (var entry : weightByBatch.entrySet()) {
			StockBatch batch = batchesById.get(entry.getKey());
			BigDecimal required = entry.getValue().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
			// This draft's own reservation covers it; anything beyond must come from unreserved stock.
			BigDecimal ownReservation = batch.getReservedWeightKg().min(required);
			BigDecimal reservedByOthers = batch.getReservedWeightKg().subtract(ownReservation);
			if (batch.getRemainingWeightKg().subtract(reservedByOthers).compareTo(required) < 0) {
				throw new BusinessException("INSUFFICIENT_STOCK",
						"Insufficient stock for chicken type " + batch.getChickenType().getId(), HttpStatus.CONFLICT);
			}
		}
		for (var entry : weightByBatch.entrySet()) {
			StockBatch batch = batchesById.get(entry.getKey());
			batch.setReservedWeightKg(batch.getReservedWeightKg().subtract(
					batch.getReservedWeightKg().min(entry.getValue())));
			batch.setRemainingWeightKg(batch.getRemainingWeightKg().subtract(entry.getValue()));
			stockBatches.save(batch);
		}

		for (DistributionItem item : distribution.getItems()) {
			InventoryMovement movement = new InventoryMovement();
			movement.setStockBatch(item.getStockBatch());
			movement.setType(InventoryMovement.MovementType.DISTRIBUTION);
			movement.setWeightKg(item.getWeightKg());
			movement.setReferenceType("DISTRIBUTION");
			movement.setReferenceId(distribution.getId());
			movements.save(movement);
		}
		distribution.setStatus(DistributionStatus.DISPATCHED);
		return toResponse(distributions.save(distribution));
	}

	@Transactional
	@Audited(action = "DISTRIBUTION_RECEIVE", entityType = "Distribution")
	public BranchReceiptResponse receive(UUID id, BranchReceiptRequest request) {
		Distribution distribution = findAccessible(id);
		branches.lockById(distribution.getBranch().getId());
		if (distribution.getStatus() != DistributionStatus.DISPATCHED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Only dispatched distributions can be received", HttpStatus.CONFLICT);
		}

		Set<UUID> expectedLines = new HashSet<>();
		distribution.getItems().forEach(item -> expectedLines.add(item.getId()));
		Set<UUID> requestedLines = new HashSet<>();
		for (var itemRequest : request.items()) {
			if (!requestedLines.add(itemRequest.distributionItemId())) {
				throw new BusinessException("VALIDATION_FAILED",
						"Distribution line " + itemRequest.distributionItemId() + " appears more than once",
						HttpStatus.BAD_REQUEST);
			}
		}
		if (!requestedLines.containsAll(expectedLines)) {
			throw new BusinessException("VALIDATION_FAILED",
					"Every distribution line must be received; record a discrepancy for missing weight",
					HttpStatus.BAD_REQUEST);
		}

		BranchReceipt receipt = new BranchReceipt();
		receipt.setDistribution(distribution);
		receipt.setReceivedAt(request.receivedAt());
		receipt.setNotes(request.notes());

		for (var itemRequest : request.items()) {
			DistributionItem item = distribution.getItems().stream()
					.filter(candidate -> candidate.getId().equals(itemRequest.distributionItemId()))
					.findFirst()
					.orElseThrow(() -> new ResourceNotFoundException("DistributionItem", itemRequest.distributionItemId()));

			if (itemRequest.receivedWeightKg().compareTo(item.getWeightKg()) > 0) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Received weight cannot exceed distributed weight", HttpStatus.BAD_REQUEST);
			}

			BigDecimal discrepancy = item.getWeightKg().subtract(itemRequest.receivedWeightKg())
					.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);

			BranchReceiptItem receiptItem = new BranchReceiptItem();
			receiptItem.setBranchReceipt(receipt);
			receiptItem.setDistributionItem(item);
			receiptItem.setReceivedWeightKg(itemRequest.receivedWeightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
			receiptItem.setDiscrepancyWeightKg(discrepancy);
			receiptItem.setDiscrepancyReason(itemRequest.discrepancyReason());
			if (discrepancy.compareTo(BigDecimal.ZERO) > 0 && itemRequest.discrepancyReason() == null) {
				throw new BusinessException("VALIDATION_FAILED",
						"Discrepancy reason is required", HttpStatus.BAD_REQUEST);
			}
			receipt.getItems().add(receiptItem);

			if (discrepancy.compareTo(BigDecimal.ZERO) > 0) {
				InventoryMovement movement = new InventoryMovement();
				movement.setStockBatch(item.getStockBatch());
				movement.setType(InventoryMovement.MovementType.LOSS);
				movement.setWeightKg(discrepancy);
				movement.setReferenceType("DISTRIBUTION");
				movement.setReferenceId(distribution.getId());
				movement.setReason(itemRequest.discrepancyReason() == null ? null : itemRequest.discrepancyReason().name());
				movements.save(movement);
			}
		}

		distribution.getReceipts().add(receipt);
		distribution.setStatus(DistributionStatus.RECEIVED);
		branchReceipts.save(receipt);
		distributions.save(distribution);
		return mapper.toReceiptResponse(receipt);
	}

	@Transactional
	@Audited(action = "DISTRIBUTION_REVERSE", entityType = "Distribution")
	public DistributionResponse reverse(UUID id, DistributionReverseRequest request) {
		Distribution distribution = findAccessible(id);
		branches.lockById(distribution.getBranch().getId());
		if (distribution.getStatus() == DistributionStatus.DRAFT) {
			// Drafts only reserved stock; release the reservation.
			Map<UUID, BigDecimal> reservedByBatch = new LinkedHashMap<>();
			for (DistributionItem item : distribution.getItems()) {
				reservedByBatch.merge(item.getStockBatch().getId(), item.getWeightKg(), BigDecimal::add);
			}
			for (StockBatch batch : stockBatches.lockAllById(reservedByBatch.keySet())) {
				BigDecimal release = batch.getReservedWeightKg().min(reservedByBatch.get(batch.getId()));
				batch.setReservedWeightKg(batch.getReservedWeightKg().subtract(release));
				stockBatches.save(batch);
			}
			distribution.setStatus(DistributionStatus.REVERSED);
			distribution.setReversalReason(request.reason());
			return toResponse(distributions.save(distribution));
		}
		if (distribution.getStatus() == DistributionStatus.DISPATCHED || distribution.getStatus() == DistributionStatus.RECEIVED) {
			// Weight that comes back to the warehouse: the full dispatch if the branch never received it,
			// otherwise only what the branch received. Recorded discrepancies stay as losses.
			Map<DistributionItem, BigDecimal> returnedWeight = returnedWeightByLine(distribution);
			if (distribution.getStatus() == DistributionStatus.RECEIVED) {
				ensureBranchStillHoldsStock(distribution, returnedWeight);
			}
			for (DistributionItem item : distribution.getItems()) {
				BigDecimal returned = returnedWeight.get(item);
				if (returned.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				StockBatch batch = item.getStockBatch();
				batch.setRemainingWeightKg(batch.getRemainingWeightKg().add(returned));
				stockBatches.save(batch);

				InventoryMovement movement = new InventoryMovement();
				movement.setStockBatch(batch);
				movement.setType(InventoryMovement.MovementType.REVERSAL);
				movement.setWeightKg(returned);
				movement.setReferenceType("DISTRIBUTION");
				movement.setReferenceId(distribution.getId());
				movement.setReason(request.reason());
				movements.save(movement);
			}
			distribution.setStatus(DistributionStatus.REVERSED);
			distribution.setReversalReason(request.reason());
			return toResponse(distributions.save(distribution));
		}
		throw new BusinessException("INVALID_STATUS_TRANSITION",
				"Distribution cannot be reversed in its current status", HttpStatus.CONFLICT);
	}

	private Map<DistributionItem, BigDecimal> returnedWeightByLine(Distribution distribution) {
		Map<DistributionItem, BigDecimal> returned = new IdentityHashMap<>();
		boolean received = distribution.getStatus() == DistributionStatus.RECEIVED;
		for (DistributionItem item : distribution.getItems()) {
			returned.put(item, received ? BigDecimal.ZERO : item.getWeightKg());
		}
		if (received) {
			for (BranchReceipt receipt : distribution.getReceipts()) {
				for (BranchReceiptItem receiptItem : receipt.getItems()) {
					returned.merge(receiptItem.getDistributionItem(), receiptItem.getReceivedWeightKg(), BigDecimal::add);
				}
			}
		}
		return returned;
	}

	private void ensureBranchStillHoldsStock(Distribution distribution, Map<DistributionItem, BigDecimal> returnedWeight) {
		Map<UUID, BigDecimal> returnedByType = new HashMap<>();
		returnedWeight.forEach((item, weight) ->
				returnedByType.merge(item.getChickenType().getId(), weight, BigDecimal::add));
		Map<UUID, BigDecimal> available = branchSales.availableWeightByChickenType(distribution.getBranch().getId());
		for (var entry : returnedByType.entrySet()) {
			BigDecimal held = available.getOrDefault(entry.getKey(), BigDecimal.ZERO);
			if (held.compareTo(entry.getValue()) < 0) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"The branch has already sold stock from this distribution; correct the branch sales first",
						HttpStatus.CONFLICT);
			}
		}
	}

	private Instant endOfDay(LocalDate date) {
		return BusinessTime.endOf(date);
	}

	private Distribution findAccessible(UUID id) {
		Distribution distribution = distributions.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Distribution", id));
		branchAccess.require(distribution.getBranch().getId());
		return distribution;
	}

	private void recalculate(Distribution distribution) {
		BigDecimal totalWeight = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal totalTransferValue = BigDecimal.ZERO;
		BigDecimal totalOurMargin = BigDecimal.ZERO;
		for (DistributionItem item : distribution.getItems()) {
			item.setLineCost(item.getWeightKg().multiply(item.getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setLineTransferValue(item.getWeightKg()
					.multiply(item.getTransferPricePerKg())
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			totalWeight = totalWeight.add(item.getWeightKg());
			totalCost = totalCost.add(item.getLineCost());
			totalTransferValue = totalTransferValue.add(item.getLineTransferValue());
			totalOurMargin = totalOurMargin.add(item.getWeightKg()
					.multiply(item.getOurMarginPerKg())
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		}
		distribution.setTotalWeightKg(totalWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		distribution.setTotalCost(totalCost);
		distribution.setTotalTransferValue(totalTransferValue);
		distribution.setTotalOurMargin(totalOurMargin);
		distribution.setExpenseAmount(distribution.getExpenses().stream()
				.map(DistributionExpense::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
	}

	private void allocateExpenses(Distribution distribution) {
		if (distribution.getExpenses().isEmpty() || distribution.getItems().isEmpty()) {
			return;
		}
		BigDecimal totalWeight = distribution.getItems().stream()
				.map(DistributionItem::getWeightKg)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}

		Map<DistributionItem, BigDecimal> allocatedPerItem = new IdentityHashMap<>();
		for (DistributionItem item : distribution.getItems()) {
			allocatedPerItem.put(item, BigDecimal.ZERO);
		}

		for (DistributionExpense expense : distribution.getExpenses()) {
			if (expense.getAllocationMethod() == DistributionExpense.AllocationMethod.MANUAL) {
				continue;
			}
			BigDecimal allocated = BigDecimal.ZERO;
			for (int i = 0; i < distribution.getItems().size(); i++) {
				DistributionItem item = distribution.getItems().get(i);
				BigDecimal amount;
				if (i == distribution.getItems().size() - 1) {
					amount = expense.getAmount().subtract(allocated);
				} else {
					amount = expense.getAmount().multiply(item.getWeightKg())
							.divide(totalWeight, MONEY_SCALE, RoundingMode.HALF_UP);
					allocated = allocated.add(amount);
				}
				amount = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
				ExpenseAllocation allocation = new ExpenseAllocation();
				allocation.setDistributionExpense(expense);
				allocation.setDistributionItem(item);
				allocation.setAmount(amount);
				expenseAllocations.save(allocation);
				allocatedPerItem.merge(item, amount, BigDecimal::add);
			}
		}

		for (DistributionItem item : distribution.getItems()) {
			BigDecimal allocated = allocatedPerItem.getOrDefault(item, BigDecimal.ZERO);
			BigDecimal expensePerKg = item.getWeightKg().compareTo(BigDecimal.ZERO) == 0
					? BigDecimal.ZERO.setScale(COST_SCALE)
					: allocated.divide(item.getWeightKg(), COST_SCALE, RoundingMode.HALF_UP);
			item.setDistributionExpensePerKg(expensePerKg);
			item.setTransferPricePerKg(item.getUnitCost()
					.add(item.getOurMarginPerKg())
					.add(expensePerKg));
		}
	}

	private DistributionResponse toResponse(Distribution distribution) {
		return mapper.toResponse(distribution);
	}

	private static class AllocatedItem {
		final DistributionItemRequest request;
		final ChickenType chickenType;
		final StockBatch batch;
		final BigDecimal weight;

		AllocatedItem(DistributionItemRequest request, ChickenType chickenType, StockBatch batch, BigDecimal weight) {
			this.request = request;
			this.chickenType = chickenType;
			this.batch = batch;
			this.weight = weight;
		}
	}
}
