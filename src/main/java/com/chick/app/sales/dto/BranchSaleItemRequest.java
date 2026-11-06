package com.chick.app.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Stock of one chicken type the branch counted at the end of the day. */
public record BranchSaleItemRequest(
		@NotNull UUID chickenTypeId,
		@NotNull @DecimalMin("0") BigDecimal closingWeightKg) {
}
