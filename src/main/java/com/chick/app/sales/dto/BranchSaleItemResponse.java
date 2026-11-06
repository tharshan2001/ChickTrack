package com.chick.app.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchSaleItemResponse(
		UUID id,
		UUID chickenTypeId,
		BigDecimal openingWeightKg,
		BigDecimal closingWeightKg,
		BigDecimal weightKg,
		BigDecimal sellingPricePerKg,
		BigDecimal transferPricePerKg,
		BigDecimal revenue,
		BigDecimal cost,
		BigDecimal profit) {
}
