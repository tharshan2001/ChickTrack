package com.chick.app.distribution.dto;

import com.chick.app.distribution.domain.DistributionExpense.AllocationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DistributionExpenseRequest(
		UUID categoryId,
		String description,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		AllocationMethod allocationMethod,
		/** Required for MANUAL: the amount each chicken type carries; must add up to {@code amount}. */
		List<@Valid ManualExpenseShareRequest> manualShares) {
}
