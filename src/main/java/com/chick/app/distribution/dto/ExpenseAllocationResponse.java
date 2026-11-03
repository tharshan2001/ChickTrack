package com.chick.app.distribution.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseAllocationResponse(
		UUID id,
		UUID distributionExpenseId,
		UUID distributionItemId,
		BigDecimal amount) {
}
