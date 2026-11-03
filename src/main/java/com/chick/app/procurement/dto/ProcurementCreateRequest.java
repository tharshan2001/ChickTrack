package com.chick.app.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProcurementCreateRequest(
		@NotNull UUID supplierId,
			@NotNull LocalDate procurementDate,
		String notes,
		@NotEmpty @Valid List<ProcurementItemRequest> items,
		List<@Valid ProcurementExpenseRequest> expenses) {
}
