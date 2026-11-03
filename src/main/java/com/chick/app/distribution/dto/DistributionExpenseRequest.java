package com.chick.app.distribution.dto;

import com.chick.app.distribution.domain.DistributionExpense.AllocationMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record DistributionExpenseRequest(
		UUID categoryId,
		String description,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		AllocationMethod allocationMethod) {
}
