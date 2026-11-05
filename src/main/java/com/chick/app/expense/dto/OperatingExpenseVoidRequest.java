package com.chick.app.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OperatingExpenseVoidRequest(@NotBlank @Size(max = 2000) String reason) {
}
