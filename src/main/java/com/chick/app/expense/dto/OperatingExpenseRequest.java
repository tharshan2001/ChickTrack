package com.chick.app.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Create or update an operating expense. Leave {@code branchId} out for a head-office expense. */
public record OperatingExpenseRequest(
		@NotNull LocalDate expenseDate,
		@NotNull UUID categoryId,
		UUID branchId,
		@NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
		@Size(max = 150) String payee,
		@Size(max = 2000) String description) {
}
