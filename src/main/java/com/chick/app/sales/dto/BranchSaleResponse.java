package com.chick.app.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One day close of a branch. {@code totalSales} is what the branch entered; {@code transferValue} is the
 * cost of the weight that left the branch; {@code variance} is total sales minus expected sales
 * (negative = short), null when expected sales are unknown.
 */
public record BranchSaleResponse(
		UUID id,
		String reference,
		UUID branchId,
		LocalDate saleDate,
		BigDecimal totalSales,
		BigDecimal expectedSales,
		BigDecimal variance,
		BigDecimal transferValue,
		BigDecimal branchProfit,
		String notes,
		List<BranchSaleItemResponse> items,
		Instant createdAt,
		Instant updatedAt) {
}
