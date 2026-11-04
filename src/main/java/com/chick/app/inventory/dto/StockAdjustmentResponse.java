package com.chick.app.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockAdjustmentResponse(
		UUID id,
		String reference,
		UUID stockBatchId,
		String type,
		BigDecimal weightChangeKg,
		BigDecimal remainingWeightKg,
		String reason,
		Instant createdAt) {
}
