package com.chick.app.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
		UUID id,
		UUID stockBatchId,
		UUID chickenTypeId,
		String type,
		BigDecimal weightKg,
		String referenceType,
		UUID referenceId,
		String reason,
		Instant createdAt) {
}
