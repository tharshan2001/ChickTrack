package com.chick.app.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BranchSaleResponse(
		UUID id,
		String reference,
		UUID branchId,
		LocalDate saleDate,
		BigDecimal totalSales,
		BigDecimal costAmount,
		BigDecimal profit,
		String notes,
		Instant createdAt,
		Instant updatedAt) {
}
