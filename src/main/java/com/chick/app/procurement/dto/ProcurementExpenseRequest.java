package com.chick.app.procurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProcurementExpenseRequest(
		UUID categoryId,
		String description,
		@NotNull @DecimalMin("0.01") BigDecimal amount) {
}
