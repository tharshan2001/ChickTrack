package com.chick.app.procurement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcurementReceiptItemResponse(
		UUID id,
		UUID procurementItemId,
		BigDecimal receivedWeightKg) {
}
