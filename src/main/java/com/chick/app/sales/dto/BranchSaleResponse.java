package com.chick.app.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BranchSaleResponse(
		UUID id,
		String reference,
		UUID branchId,
		LocalDate saleDate,
		BigDecimal totalSales,
		BigDecimal transferValue,
		BigDecimal branchProfit,
		String notes,
		List<BranchSaleItemResponse> items,
		Instant createdAt,
		Instant updatedAt) {
}
