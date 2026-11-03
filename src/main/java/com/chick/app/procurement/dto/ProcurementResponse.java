package com.chick.app.procurement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementResponse(
		UUID id,
		String reference,
		UUID supplierId,
		LocalDate procurementDate,
		String status,
		BigDecimal purchaseValue,
		BigDecimal commissionAmount,
		BigDecimal expenseAmount,
		BigDecimal totalCost,
		BigDecimal paidWeightKg,
		BigDecimal receivedWeightKg,
		BigDecimal landedCostPerKg,
		String notes,
		String reversalReason,
		List<ProcurementItemResponse> items,
		List<ProcurementExpenseResponse> expenses,
		List<ProcurementReceiptResponse> receipts,
		Instant createdAt,
		Instant updatedAt) {
}
