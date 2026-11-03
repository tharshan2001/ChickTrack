package com.chick.app.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DailyPriceCreateRequest(
		@NotNull UUID chickenTypeId,
		UUID branchId,
		UUID supplierId,
		@NotNull @DecimalMin("0.0000") BigDecimal purchasePricePerKg,
		@NotNull @DecimalMin("0.0000") BigDecimal sellingPricePerKg,
		@NotNull Instant effectiveFrom) {
}
