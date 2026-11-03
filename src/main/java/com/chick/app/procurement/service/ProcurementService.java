package com.chick.app.procurement.service;

import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.expense.domain.ExpenseCategory;
import com.chick.app.expense.repository.ExpenseCategoryRepository;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.InventoryMovementRepository;
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
	private final InventoryMovementRepository movements;
	private final DocumentNumberService documentNumbers;

	public ProcurementService(
			ProcurementRepository procurements,
			ProcurementMapper mapper,
			SupplierRepository suppliers,
			ChickenTypeRepository chickenTypes,
			ExpenseCategoryRepository expenseCategories,
			StockBatchRepository stockBatches,
			InventoryMovementRepository movements,
			DocumentNumberService documentNumbers) {
		this.procurements = procurements;
		this.mapper = mapper;
		this.suppliers = suppliers;
		this.chickenTypes = chickenTypes;
		this.expenseCategories = expenseCategories;
		this.stockBatches = stockBatches;
		this.movements = movements;
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
			item.setReceivedWeightKg(itemRequest.paidWeightKg());
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
		procurement.setStatus(ProcurementStatus.RECEIVED);
		Procurement saved = procurements.save(procurement);

		for (ProcurementItem item : saved.getItems()) {
			if (item.getReceivedWeightKg().compareTo(BigDecimal.ZERO) > 0 && item.getLandedCostPerKg() != null) {
				StockBatch batch = new StockBatch();
				batch.setProcurementItem(item);
				batch.setChickenType(item.getChickenType());
				batch.setReceivedWeightKg(item.getReceivedWeightKg());
				batch.setRemainingWeightKg(item.getReceivedWeightKg());
				batch.setLandedCostPerKg(item.getLandedCostPerKg());
				batch.setReceivedAt(java.time.Instant.now());
				stockBatches.save(batch);

				InventoryMovement movement = new InventoryMovement();
				movement.setStockBatch(batch);
				movement.setType(InventoryMovement.MovementType.RECEIPT);
				movement.setWeightKg(item.getReceivedWeightKg());
				movement.setReferenceType("PROCUREMENT");
				movement.setReferenceId(saved.getId());
				movements.save(movement);
			}
		}

		return toResponse(saved);
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
