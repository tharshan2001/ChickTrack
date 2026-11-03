package com.chick.app.distribution.dto;

import com.chick.app.distribution.domain.DistributionExpense.AllocationMethod;
import java.math.BigDecimal;
import java.util.UUID;

public record DistributionExpenseResponse(
		UUID id,
		UUID categoryId,
		String description,
		BigDecimal amount,
		AllocationMethod allocationMethod) {
}
