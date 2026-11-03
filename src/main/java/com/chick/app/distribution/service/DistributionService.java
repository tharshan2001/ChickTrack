package com.chick.app.distribution.service;

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
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
			DocumentNumberService documentNumbers) {
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
	}

	@Transactional(readOnly = true)
	public Page<DistributionResponse> list(Pageable pageable, UUID branchId, DistributionStatus status, LocalDate from, LocalDate to) {
		Specification<Distribution> specification = (root, query, cb) -> cb.conjunction();
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
		return toResponse(find(id));
	}

	@Transactional
	public DistributionResponse create(DistributionCreateRequest request) {
		Branch branch = branches.findById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		Distribution distribution = mapper.toEntity(request);
		distribution.setBranch(branch);
		distribution.setStatus(DistributionStatus.DRAFT);
		distribution.setReference(documentNumbers.next("DISTRIBUTION", "DST"));

		// Validate and allocate stock first; do not mutate stock until all items are valid.
		List<AllocatedItem> allocations = new ArrayList<>();
		for (DistributionItemRequest itemRequest : request.items()) {
			ChickenType chickenType = chickenTypes.findById(itemRequest.chickenTypeId())
					.orElseThrow(() -> new ResourceNotFoundException("ChickenType", itemRequest.chickenTypeId()));
			List<StockBatch> batches = stockBatches.findAvailableByChickenType(chickenType);
			BigDecimal remainingToAllocate = itemRequest.weightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
			BigDecimal available = batches.stream().map(StockBatch::getRemainingWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
			if (available.compareTo(remainingToAllocate) < 0) {
				throw new BusinessException("INSUFFICIENT_STOCK",
						"Insufficient stock for chicken type " + chickenType.getId(), HttpStatus.CONFLICT);
			}

			BigDecimal allocatedWeight = BigDecimal.ZERO;
			for (StockBatch batch : batches) {
				if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
					break;
				}
				BigDecimal allocate = batch.getRemainingWeightKg().min(remainingToAllocate);
				if (allocate.compareTo(BigDecimal.ZERO) <= 0) {
					continue;
				}
				allocations.add(new AllocatedItem(itemRequest, chickenType, batch, allocate));
				remainingToAllocate = remainingToAllocate.subtract(allocate);
				allocatedWeight = allocatedWeight.add(allocate);
			}
			if (remainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
				throw new BusinessException("INSUFFICIENT_STOCK",
						"Unable to allocate stock for chicken type " + chickenType.getId(), HttpStatus.CONFLICT);
			}
		}

		// Mutate stock and create distribution items.
		for (AllocatedItem allocation : allocations) {
			StockBatch batch = allocation.batch;
			batch.setRemainingWeightKg(batch.getRemainingWeightKg().subtract(allocation.weight));
			stockBatches.save(batch);

			DistributionItem item = new DistributionItem();
			item.setDistribution(distribution);
			item.setStockBatch(batch);
			item.setChickenType(allocation.chickenType);
			item.setWeightKg(allocation.weight);
			item.setUnitCost(batch.getLandedCostPerKg());
			item.setUnitPrice(allocation.request.sellingPricePerKg().setScale(COST_SCALE, RoundingMode.HALF_UP));
			item.setLineCost(item.getWeightKg().multiply(item.getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setLineRevenue(item.getWeightKg().multiply(item.getUnitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setLineMargin(item.getLineRevenue().subtract(item.getLineCost()));
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

		recalculate(distribution);
		distributions.save(distribution);
		allocateExpenses(distribution);
		distributions.save(distribution);
		return toResponse(distribution);
	}

	@Transactional
	public DistributionResponse dispatch(UUID id) {
		Distribution distribution = find(id);
		if (distribution.getStatus() != DistributionStatus.DRAFT) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Only draft distributions can be dispatched", HttpStatus.CONFLICT);
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
	public BranchReceiptResponse receive(UUID id, BranchReceiptRequest request) {
		Distribution distribution = find(id);
		if (distribution.getStatus() != DistributionStatus.DISPATCHED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Only dispatched distributions can be received", HttpStatus.CONFLICT);
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
	public DistributionResponse reverse(UUID id, DistributionReverseRequest request) {
		Distribution distribution = find(id);
		if (distribution.getStatus() == DistributionStatus.DRAFT) {
			// Restore reserved stock.
			for (DistributionItem item : distribution.getItems()) {
				StockBatch batch = item.getStockBatch();
				batch.setRemainingWeightKg(batch.getRemainingWeightKg().add(item.getWeightKg()));
				stockBatches.save(batch);
			}
			distribution.setStatus(DistributionStatus.REVERSED);
			distribution.setReversalReason(request.reason());
			return toResponse(distributions.save(distribution));
		}
		if (distribution.getStatus() == DistributionStatus.DISPATCHED || distribution.getStatus() == DistributionStatus.RECEIVED) {
			for (DistributionItem item : distribution.getItems()) {
				StockBatch batch = item.getStockBatch();
				batch.setRemainingWeightKg(batch.getRemainingWeightKg().add(item.getWeightKg()));
				stockBatches.save(batch);

				InventoryMovement movement = new InventoryMovement();
				movement.setStockBatch(batch);
				movement.setType(InventoryMovement.MovementType.REVERSAL);
				movement.setWeightKg(item.getWeightKg());
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

	private Distribution find(UUID id) {
		return distributions.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Distribution", id));
	}

	private void recalculate(Distribution distribution) {
		BigDecimal totalWeight = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal totalRevenue = BigDecimal.ZERO;
		for (DistributionItem item : distribution.getItems()) {
			item.setLineCost(item.getWeightKg().multiply(item.getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setLineRevenue(item.getWeightKg().multiply(item.getUnitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setLineMargin(item.getLineRevenue().subtract(item.getLineCost()));
			totalWeight = totalWeight.add(item.getWeightKg());
			totalCost = totalCost.add(item.getLineCost());
			totalRevenue = totalRevenue.add(item.getLineRevenue());
		}
		distribution.setTotalWeightKg(totalWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		distribution.setTotalCost(totalCost);
		distribution.setTotalRevenue(totalRevenue);
		distribution.setTotalMargin(totalRevenue.subtract(totalCost));
		distribution.setExpenseAmount(distribution.getExpenses().stream()
				.map(DistributionExpense::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add));
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
		for (DistributionExpense expense : distribution.getExpenses()) {
			if (expense.getAllocationMethod() != DistributionExpense.AllocationMethod.BY_WEIGHT) {
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
				ExpenseAllocation allocation = new ExpenseAllocation();
				allocation.setDistributionExpense(expense);
				allocation.setDistributionItem(item);
				allocation.setAmount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				expenseAllocations.save(allocation);
				// Need repository; instead store via cascade? ExpenseAllocation has no cascade from expense. We'll save through a repository.
			}
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
