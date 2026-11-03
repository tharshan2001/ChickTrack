package com.chick.app.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcurementItemResponse(
		UUID id,
		UUID chickenTypeId,
		BigDecimal paidWeightKg,
		BigDecimal purchasePricePerKg,
		String commissionMethod,
		BigDecimal commissionValue,
		Integer quantity,
		BigDecimal purchaseValue,
		BigDecimal commissionAmount,
		BigDecimal allocatedExpenseAmount,
		BigDecimal totalCost,
		BigDecimal receivedWeightKg,
		BigDecimal landedCostPerKg) {
}
