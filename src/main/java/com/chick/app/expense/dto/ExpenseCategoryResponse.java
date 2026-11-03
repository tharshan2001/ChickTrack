package com.chick.app.expense.dto;

import java.time.Instant;
import java.util.UUID;

public record ExpenseCategoryResponse(
		UUID id,
		String name,
		String description,
		boolean active,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
