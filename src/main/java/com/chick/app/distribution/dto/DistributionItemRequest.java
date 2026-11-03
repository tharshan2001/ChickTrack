package com.chick.app.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record DistributionItemRequest(
		@NotNull UUID chickenTypeId,
		@NotNull @DecimalMin("0.001") BigDecimal weightKg,
		@NotNull @DecimalMin("0.0000") BigDecimal sellingPricePerKg) {
}
