package com.chick.app.procurement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcurementReceiptResponse(
		UUID id,
		UUID procurementId,
		Instant receivedAt,
		String notes,
		List<ProcurementReceiptItemResponse> items) {
}
