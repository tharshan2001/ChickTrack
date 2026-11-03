package com.chick.app.distribution.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DistributionResponse(
		UUID id,
		String reference,
		UUID branchId,
		LocalDate distributionDate,
		String status,
		BigDecimal totalWeightKg,
		BigDecimal totalCost,
		BigDecimal totalRevenue,
		BigDecimal totalMargin,
		BigDecimal expenseAmount,
		String notes,
		String reversalReason,
		List<DistributionItemResponse> items,
		List<DistributionExpenseResponse> expenses,
		List<BranchReceiptResponse> receipts,
		Instant createdAt,
		Instant updatedAt) {
}
