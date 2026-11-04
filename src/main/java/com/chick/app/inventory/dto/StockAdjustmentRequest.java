package com.chick.app.inventory.dto;

import com.chick.app.inventory.domain.StockAdjustment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code DAMAGE} and {@code LOSS} remove weight, {@code RETURN} adds it back. {@code ADJUSTMENT}
 * needs a {@code direction} of {@code INCREASE} or {@code DECREASE}.
 */
public record StockAdjustmentRequest(
		@NotNull UUID stockBatchId,
		@NotNull StockAdjustment.Type type,
		@NotNull @DecimalMin("0.001") BigDecimal weightKg,
		Direction direction,
		@NotBlank String reason) {

	public enum Direction { INCREASE, DECREASE }
}
