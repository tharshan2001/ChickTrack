package com.chick.app.procurement.service;

import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.expense.domain.ExpenseCategory;
import com.chick.app.expense.repository.ExpenseCategoryRepository;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.procurement.domain.CommissionMethod;
import com.chick.app.procurement.domain.Procurement;
import com.chick.app.procurement.domain.ProcurementExpense;
import com.chick.app.procurement.domain.ProcurementItem;
import com.chick.app.procurement.domain.ProcurementReceipt;
import com.chick.app.procurement.domain.ProcurementReceiptItem;
import com.chick.app.procurement.domain.ProcurementStatus;
import com.chick.app.procurement.dto.ProcurementCreateRequest;
import com.chick.app.procurement.dto.ProcurementExpenseRequest;
import com.chick.app.procurement.dto.ProcurementItemRequest;
import com.chick.app.procurement.dto.ProcurementReceiptItemRequest;
import com.chick.app.procurement.dto.ProcurementReceiptRequest;
import com.chick.app.procurement.dto.ProcurementReceiptResponse;
import com.chick.app.procurement.dto.ProcurementResponse;
import com.chick.app.procurement.dto.ProcurementReverseRequest;
import com.chick.app.procurement.mapper.ProcurementMapper;
import com.chick.app.procurement.repository.ProcurementRepository;
import com.chick.app.supplier.domain.Supplier;
import com.chick.app.supplier.repository.SupplierRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcurementService {

	private static final int MONEY_SCALE = 2;
	private static final int COST_SCALE = 4;
	private static final int WEIGHT_SCALE = 3;

	private final ProcurementRepository procurements;
	private final ProcurementMapper mapper;
	private final SupplierRepository suppliers;
	private final ChickenTypeRepository chickenTypes;
	private final ExpenseCategoryRepository expenseCategories;
	private final StockBatchRepository stockBatches;
	private final DocumentNumberService documentNumbers;

	public ProcurementService(
			ProcurementRepository procurements,
			ProcurementMapper mapper,
			SupplierRepository suppliers,
			ChickenTypeRepository chickenTypes,
			ExpenseCategoryRepository expenseCategories,
			StockBatchRepository stockBatches,
			DocumentNumberService documentNumbers) {
		this.procurements = procurements;
		this.mapper = mapper;
		this.suppliers = suppliers;
		this.chickenTypes = chickenTypes;
		this.expenseCategories = expenseCategories;
		this.stockBatches = stockBatches;
		this.documentNumbers = documentNumbers;
	}

	@Transactional(readOnly = true)
	public Page<ProcurementResponse> list(
			Pageable pageable,
			UUID supplierId,
			ProcurementStatus status,
			LocalDate from,
			LocalDate to) {
		Specification<Procurement> specification = (root, query, cb) -> cb.conjunction();
		if (supplierId != null) {
			specification = specification.and((root, query, cb) ->
					cb.equal(root.get("supplier").get("id"), supplierId));
		}
		if (status != null) {
			specification = specification.and((root, query, cb) ->
					cb.equal(root.get("status"), status));
		}
		if (from != null) {
			specification = specification.and((root, query, cb) ->
					cb.greaterThanOrEqualTo(root.get("procurementDate"), from));
		}
		if (to != null) {
			specification = specification.and((root, query, cb) ->
					cb.lessThanOrEqualTo(root.get("procurementDate"), to));
		}
		return procurements.findAll(specification, pageable).map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public ProcurementResponse get(UUID id) {
		return toResponse(find(id));
	}

	@Transactional
	public ProcurementResponse create(ProcurementCreateRequest request) {
		Supplier supplier = suppliers.findById(request.supplierId())
				.orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

		Procurement procurement = mapper.toEntity(request);
		procurement.setSupplier(supplier);
		procurement.setStatus(ProcurementStatus.DRAFT);
		procurement.setReference(documentNumbers.next("PROCUREMENT", "PRC"));

		for (ProcurementItemRequest itemRequest : request.items()) {
			ChickenType chickenType = chickenTypes.findById(itemRequest.chickenTypeId())
					.orElseThrow(() -> new ResourceNotFoundException("ChickenType", itemRequest.chickenTypeId()));
			ProcurementItem item = mapper.toItem(itemRequest);
			item.setProcurement(procurement);
			item.setChickenType(chickenType);
			item.setCommissionValue(defaultZero(itemRequest.commissionValue()));
			procurement.getItems().add(item);
		}

		if (request.expenses() != null) {
			for (ProcurementExpenseRequest expenseRequest : request.expenses()) {
				ExpenseCategory category = null;
				if (expenseRequest.categoryId() != null) {
					category = expenseCategories.findById(expenseRequest.categoryId())
							.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", expenseRequest.categoryId()));
				}
				ProcurementExpense expense = mapper.toExpense(expenseRequest);
				expense.setProcurement(procurement);
				expense.setCategory(category);
				expense.setAmount(expenseRequest.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				procurement.getExpenses().add(expense);
			}
		}

		recalculate(procurement);
		return toResponse(procurements.save(procurement));
	}

	@Transactional
	public ProcurementResponse approve(UUID id) {
		Procurement procurement = find(id);
		if (procurement.getStatus() != ProcurementStatus.DRAFT) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Only draft procurements can be approved", HttpStatus.CONFLICT);
		}
		if (procurement.getItems().isEmpty()) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Procurement must have at least one item", HttpStatus.BAD_REQUEST);
		}
		procurement.setStatus(ProcurementStatus.APPROVED);
		return toResponse(procurements.save(procurement));
	}

	@Transactional
	public ProcurementResponse addExpense(UUID id, ProcurementExpenseRequest request) {
		Procurement procurement = find(id);
		if (procurement.getStatus() == ProcurementStatus.REVERSED || procurement.getStatus() == ProcurementStatus.CLOSED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Expenses cannot be added to this procurement", HttpStatus.CONFLICT);
		}
		ExpenseCategory category = null;
		if (request.categoryId() != null) {
			category = expenseCategories.findById(request.categoryId())
					.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", request.categoryId()));
		}
		ProcurementExpense expense = mapper.toExpense(request);
		expense.setProcurement(procurement);
		expense.setCategory(category);
		expense.setAmount(request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		procurement.getExpenses().add(expense);
		recalculate(procurement);
		return toResponse(procurements.save(procurement));
	}

	@Transactional
	public ProcurementReceiptResponse receive(UUID id, ProcurementReceiptRequest request) {
		Procurement procurement = find(id);
		if (procurement.getStatus() != ProcurementStatus.APPROVED
				&& procurement.getStatus() != ProcurementStatus.PARTIALLY_RECEIVED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Procurement must be approved before receiving", HttpStatus.CONFLICT);
		}

		ProcurementReceipt receipt = mapper.toReceipt(request);
		receipt.setProcurement(procurement);
		receipt.setReceivedAt(request.receivedAt());

		for (ProcurementReceiptItemRequest itemRequest : request.items()) {
			ProcurementItem item = procurement.getItems().stream()
					.filter(candidate -> candidate.getId().equals(itemRequest.procurementItemId()))
					.findFirst()
					.orElseThrow(() -> new ResourceNotFoundException("ProcurementItem", itemRequest.procurementItemId()));

			BigDecimal remaining = item.getPaidWeightKg().subtract(item.getReceivedWeightKg());
			if (itemRequest.receivedWeightKg().compareTo(remaining) > 0) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Received weight exceeds remaining paid weight for item " + item.getId(),
						HttpStatus.BAD_REQUEST);
			}

			item.setReceivedWeightKg(item.getReceivedWeightKg().add(itemRequest.receivedWeightKg())
					.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));

			ProcurementReceiptItem receiptItem = mapper.toReceiptItem(itemRequest);
			receiptItem.setReceipt(receipt);
			receiptItem.setProcurementItem(item);
			receiptItem.setReceivedWeightKg(itemRequest.receivedWeightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
			receipt.getItems().add(receiptItem);
		}

		recalculate(procurement);

		for (ProcurementReceiptItem receiptItem : receipt.getItems()) {
			ProcurementItem item = receiptItem.getProcurementItem();
			if (item.getReceivedWeightKg().compareTo(BigDecimal.ZERO) > 0 && item.getLandedCostPerKg() != null) {
				StockBatch batch = new StockBatch();
				batch.setProcurementItem(item);
				batch.setChickenType(item.getChickenType());
				batch.setReceivedWeightKg(receiptItem.getReceivedWeightKg());
				batch.setRemainingWeightKg(receiptItem.getReceivedWeightKg());
				batch.setLandedCostPerKg(item.getLandedCostPerKg());
				batch.setReceivedAt(request.receivedAt());
				stockBatches.save(batch);
			}
		}

		procurement.getReceipts().add(receipt);

		boolean allReceived = procurement.getItems().stream()
				.allMatch(item -> item.getReceivedWeightKg().compareTo(item.getPaidWeightKg()) >= 0);
		boolean anyReceived = procurement.getItems().stream()
				.anyMatch(item -> item.getReceivedWeightKg().compareTo(BigDecimal.ZERO) > 0);
		if (allReceived && anyReceived) {
			procurement.setStatus(ProcurementStatus.RECEIVED);
		} else {
			procurement.setStatus(ProcurementStatus.PARTIALLY_RECEIVED);
		}

		procurements.save(procurement);
		return mapper.toReceiptResponse(receipt);
	}

	@Transactional
	public ProcurementResponse reverse(UUID id, ProcurementReverseRequest request) {
		Procurement procurement = find(id);
		if (procurement.getStatus() == ProcurementStatus.DRAFT || procurement.getStatus() == ProcurementStatus.REVERSED) {
			throw new BusinessException("INVALID_STATUS_TRANSITION",
					"Procurement cannot be reversed in its current status", HttpStatus.CONFLICT);
		}
		procurement.setStatus(ProcurementStatus.REVERSED);
		procurement.setReversalReason(request.reason());
		return toResponse(procurements.save(procurement));
	}

	private Procurement find(UUID id) {
		return procurements.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Procurement", id));
	}

	private void recalculate(Procurement procurement) {
		BigDecimal totalPaidWeight = BigDecimal.ZERO;
		BigDecimal totalPurchaseValue = BigDecimal.ZERO;
		BigDecimal totalCommission = BigDecimal.ZERO;

		for (ProcurementItem item : procurement.getItems()) {
			item.setPurchaseValue(item.getPaidWeightKg()
					.multiply(item.getPurchasePricePerKg())
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

			BigDecimal commission = calculateCommission(item);
			item.setCommissionAmount(commission.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

			totalPaidWeight = totalPaidWeight.add(item.getPaidWeightKg());
			totalPurchaseValue = totalPurchaseValue.add(item.getPurchaseValue());
			totalCommission = totalCommission.add(item.getCommissionAmount());
		}

		BigDecimal totalExpense = procurement.getExpenses().stream()
				.map(ProcurementExpense::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

		allocateExpenses(procurement.getItems(), totalExpense, totalPaidWeight);

		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal totalReceivedWeight = BigDecimal.ZERO;
		for (ProcurementItem item : procurement.getItems()) {
			item.setTotalCost(item.getPurchaseValue()
					.add(item.getCommissionAmount())
					.add(item.getAllocatedExpenseAmount())
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

			if (item.getReceivedWeightKg().compareTo(BigDecimal.ZERO) > 0) {
				item.setLandedCostPerKg(item.getTotalCost()
						.divide(item.getReceivedWeightKg(), COST_SCALE, RoundingMode.HALF_UP));
				totalReceivedWeight = totalReceivedWeight.add(item.getReceivedWeightKg());
			} else {
				item.setLandedCostPerKg(null);
			}
			totalCost = totalCost.add(item.getTotalCost());
		}

		procurement.setPaidWeightKg(totalPaidWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		procurement.setPurchaseValue(totalPurchaseValue);
		procurement.setCommissionAmount(totalCommission);
		procurement.setExpenseAmount(totalExpense);
		procurement.setTotalCost(totalCost);
		procurement.setReceivedWeightKg(totalReceivedWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		if (totalReceivedWeight.compareTo(BigDecimal.ZERO) > 0) {
			procurement.setLandedCostPerKg(totalCost.divide(totalReceivedWeight, COST_SCALE, RoundingMode.HALF_UP));
		} else {
			procurement.setLandedCostPerKg(null);
		}
	}

	private BigDecimal calculateCommission(ProcurementItem item) {
		BigDecimal value = defaultZero(item.getCommissionValue());
		return switch (item.getCommissionMethod()) {
			case FIXED -> value;
			case PER_KG -> item.getPaidWeightKg().multiply(value);
			case PERCENTAGE -> item.getPurchaseValue().multiply(value).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
			case PER_BIRD -> value.multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity()));
		};
	}

	private void allocateExpenses(List<ProcurementItem> items, BigDecimal totalExpense, BigDecimal totalPaidWeight) {
		if (items.isEmpty() || totalExpense.compareTo(BigDecimal.ZERO) <= 0 || totalPaidWeight.compareTo(BigDecimal.ZERO) <= 0) {
			for (ProcurementItem item : items) {
				item.setAllocatedExpenseAmount(BigDecimal.ZERO.setScale(MONEY_SCALE));
			}
			return;
		}

		BigDecimal allocated = BigDecimal.ZERO;
		for (int i = 0; i < items.size(); i++) {
			ProcurementItem item = items.get(i);
			BigDecimal amount;
			if (i == items.size() - 1) {
				amount = totalExpense.subtract(allocated);
			} else {
				amount = totalExpense.multiply(item.getPaidWeightKg())
						.divide(totalPaidWeight, MONEY_SCALE, RoundingMode.HALF_UP);
				allocated = allocated.add(amount);
			}
			item.setAllocatedExpenseAmount(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		}
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private ProcurementResponse toResponse(Procurement procurement) {
		return mapper.toResponse(procurement);
	}
}
