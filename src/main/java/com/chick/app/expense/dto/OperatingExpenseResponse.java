package com.chick.app.expense.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OperatingExpenseResponse(
		UUID id,
		String reference,
		LocalDate expenseDate,
		UUID categoryId,
		String categoryName,
		UUID branchId,
		String branchName,
		BigDecimal amount,
		String payee,
		String description,
		String status,
		String voidReason,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
