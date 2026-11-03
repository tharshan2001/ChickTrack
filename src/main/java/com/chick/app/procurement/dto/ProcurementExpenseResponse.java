package com.chick.app.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcurementExpenseResponse(
		UUID id,
		UUID categoryId,
		String description,
		BigDecimal amount) {
}
