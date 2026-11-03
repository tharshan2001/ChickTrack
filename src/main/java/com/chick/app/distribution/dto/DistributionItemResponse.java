package com.chick.app.distribution.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DistributionItemResponse(
		UUID id,
		UUID stockBatchId,
		UUID chickenTypeId,
		BigDecimal weightKg,
		BigDecimal unitCost,
		BigDecimal unitPrice,
		BigDecimal lineCost,
		BigDecimal lineRevenue,
		BigDecimal lineMargin) {
}
