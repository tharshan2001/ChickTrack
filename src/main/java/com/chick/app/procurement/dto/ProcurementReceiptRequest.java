package com.chick.app.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record ProcurementReceiptRequest(
		@NotNull Instant receivedAt,
		String notes,
		@NotEmpty @Valid List<ProcurementReceiptItemRequest> items) {
}
