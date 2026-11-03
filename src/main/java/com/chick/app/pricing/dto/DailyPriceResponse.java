package com.chick.app.pricing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DailyPriceResponse(
		UUID id,
		UUID chickenTypeId,
		UUID branchId,
		UUID supplierId,
		BigDecimal purchasePricePerKg,
		BigDecimal sellingPricePerKg,
		Instant effectiveFrom,
		Instant createdAt) {
}
