package com.chick.app.inventory.service;

import com.chick.app.audit.Audited;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.common.time.BusinessTime;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockAdjustment;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.dto.InventoryMovementResponse;
import com.chick.app.inventory.dto.StockAdjustmentRequest;
import com.chick.app.inventory.dto.StockAdjustmentResponse;
import com.chick.app.inventory.dto.StockBatchResponse;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.inventory.repository.StockAdjustmentRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

	private static final int WEIGHT_SCALE = 3;
	private static final int MONEY_SCALE = 2;

	private final StockBatchRepository batches;
	private final InventoryMovementRepository movements;
	private final StockAdjustmentRepository adjustments;
	private final DocumentNumberService documentNumbers;

	public InventoryService(
			StockBatchRepository batches,
			InventoryMovementRepository movements,
			StockAdjustmentRepository adjustments,
			DocumentNumberService documentNumbers) {
		this.batches = batches;
		this.movements = movements;
		this.adjustments = adjustments;
		this.documentNumbers = documentNumbers;
	}

	@Transactional(readOnly = true)
	public Page<StockBatchResponse> listBatches(Pageable pageable, UUID chickenTypeId, Boolean available) {
		Specification<StockBatch> specification = (root, query, cb) -> cb.conjunction();
		if (chickenTypeId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("chickenType").get("id"), chickenTypeId));
		}
		if (Boolean.TRUE.equals(available)) {
			specification = specification.and((root, query, cb) -> cb.greaterThan(root.get("remainingWeightKg"), BigDecimal.ZERO));
		}
		else if (Boolean.FALSE.equals(available)) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("remainingWeightKg"), BigDecimal.ZERO));
		}
		LocalDate today = BusinessTime.today();
		return batches.findAll(specification, pageable).map(batch -> toResponse(batch, today));
	}

	@Transactional(readOnly = true)
	public StockBatchResponse getBatch(UUID id) {
		return toResponse(findBatch(id), BusinessTime.today());
	}

	@Transactional(readOnly = true)
	public Page<InventoryMovementResponse> listMovements(
			Pageable pageable, UUID batchId, InventoryMovement.MovementType type, LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException("VALIDATION_FAILED", "from must not be after to", HttpStatus.BAD_REQUEST);
		}
		Specification<InventoryMovement> specification = (root, query, cb) -> cb.conjunction();
		if (batchId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("stockBatch").get("id"), batchId));
		}
		if (type != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("type"), type));
		}
		if (from != null) {
			Instant start = BusinessTime.startOf(from);
			specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
		}
		if (to != null) {
			Instant end = BusinessTime.startOf(to.plusDays(1));
			specification = specification.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
		}
		return movements.findAll(specification, pageable).map(InventoryService::toResponse);
	}

	@Transactional(readOnly = true)
	public Page<StockAdjustmentResponse> listAdjustments(Pageable pageable, UUID batchId) {
		Specification<StockAdjustment> specification = (root, query, cb) -> cb.conjunction();
		if (batchId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("stockBatch").get("id"), batchId));
		}
		return adjustments.findAll(specification, pageable).map(InventoryService::toResponse);
	}

	/**
	 * Applies a warehouse correction: locks the batch, changes its remaining weight and writes the
	 * matching ledger movement, all in one transaction.
	 */
	@Transactional
	@Audited(action = "STOCK_ADJUSTMENT", entityType = "StockAdjustment")
	public StockAdjustmentResponse adjust(StockAdjustmentRequest request) {
		BigDecimal weight = request.weightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
		boolean increase = switch (request.type()) {
			case DAMAGE, LOSS -> false;
			case RETURN -> true;
			case ADJUSTMENT -> {
				if (request.direction() == null) {
					throw new BusinessException("VALIDATION_FAILED",
							"direction (INCREASE or DECREASE) is required for ADJUSTMENT", HttpStatus.BAD_REQUEST);
				}
				yield request.direction() == StockAdjustmentRequest.Direction.INCREASE;
			}
		};

		List<StockBatch> locked = batches.lockAllById(Set.of(request.stockBatchId()));
		if (locked.isEmpty()) {
			throw new ResourceNotFoundException("StockBatch", request.stockBatchId());
		}
		StockBatch batch = locked.get(0);

		BigDecimal remaining = batch.getRemainingWeightKg();
		BigDecimal newRemaining;
		if (increase) {
			newRemaining = remaining.add(weight);
			if (newRemaining.compareTo(batch.getReceivedWeightKg()) > 0) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Remaining weight cannot exceed the batch's received weight of "
								+ batch.getReceivedWeightKg().toPlainString() + " kg",
						HttpStatus.BAD_REQUEST);
			}
		}
		else {
			if (weight.compareTo(remaining) > 0) {
				throw new BusinessException("INSUFFICIENT_STOCK",
						"Batch has only " + remaining.toPlainString() + " kg remaining", HttpStatus.CONFLICT);
			}
			newRemaining = remaining.subtract(weight);
		}

		StockAdjustment adjustment = new StockAdjustment();
		adjustment.setReference(documentNumbers.next("STOCK_ADJUSTMENT", "ADJ"));
		adjustment.setStockBatch(batch);
		adjustment.setType(request.type());
		adjustment.setWeightChangeKg(increase ? weight : weight.negate());
		adjustment.setReason(request.reason().trim());
		adjustments.save(adjustment);

		batch.setRemainingWeightKg(newRemaining);
		batches.save(batch);

		InventoryMovement movement = new InventoryMovement();
		movement.setStockBatch(batch);
		movement.setType(InventoryMovement.MovementType.valueOf(request.type().name()));
		movement.setWeightKg(weight);
		movement.setReferenceType("STOCK_ADJUSTMENT");
		movement.setReferenceId(adjustment.getId());
		movement.setReason(adjustment.getReason());
		movements.save(movement);

		return toResponse(adjustment);
	}

	private StockBatch findBatch(UUID id) {
		return batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("StockBatch", id));
	}

	private static StockBatchResponse toResponse(StockBatch batch, LocalDate today) {
		LocalDate received = BusinessTime.dateOf(batch.getReceivedAt());
		return new StockBatchResponse(
				batch.getId(),
				batch.getChickenType().getId(),
				batch.getChickenType().getName(),
				batch.getProcurementItem().getId(),
				batch.getReceivedWeightKg(),
				batch.getRemainingWeightKg(),
				batch.getLandedCostPerKg(),
				batch.getRemainingWeightKg().multiply(batch.getLandedCostPerKg()).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				batch.getReceivedAt(),
				(int) Math.max(0, ChronoUnit.DAYS.between(received, today)));
	}

	private static InventoryMovementResponse toResponse(InventoryMovement movement) {
		return new InventoryMovementResponse(
				movement.getId(),
				movement.getStockBatch().getId(),
				movement.getStockBatch().getChickenType().getId(),
				movement.getType().name(),
				movement.getWeightKg(),
				movement.getReferenceType(),
				movement.getReferenceId(),
				movement.getReason(),
				movement.getCreatedAt());
	}

	private static StockAdjustmentResponse toResponse(StockAdjustment adjustment) {
		return new StockAdjustmentResponse(
				adjustment.getId(),
				adjustment.getReference(),
				adjustment.getStockBatch().getId(),
				adjustment.getType().name(),
				adjustment.getWeightChangeKg(),
				adjustment.getStockBatch().getRemainingWeightKg(),
				adjustment.getReason(),
				adjustment.getCreatedAt());
	}
}
