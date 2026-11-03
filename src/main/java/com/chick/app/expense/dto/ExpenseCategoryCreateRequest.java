package com.chick.app.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryCreateRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 255) String description) {
}
