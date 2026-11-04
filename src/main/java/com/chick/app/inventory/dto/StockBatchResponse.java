package com.chick.app.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockBatchResponse(
		UUID id,
		UUID chickenTypeId,
		String chickenTypeName,
		UUID procurementItemId,
		BigDecimal receivedWeightKg,
		BigDecimal remainingWeightKg,
		BigDecimal reservedWeightKg,
		BigDecimal availableWeightKg,
		BigDecimal landedCostPerKg,
		BigDecimal remainingValue,
		Instant receivedAt,
		int ageDays) {
}
