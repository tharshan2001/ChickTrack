package com.chick.app.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record BranchSaleItemRequest(
		@NotNull UUID chickenTypeId,
		@NotNull @DecimalMin("0.001") BigDecimal weightKg) {
}
