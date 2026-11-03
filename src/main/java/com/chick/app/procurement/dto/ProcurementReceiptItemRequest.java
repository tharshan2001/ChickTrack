package com.chick.app.procurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProcurementReceiptItemRequest(
		@NotNull UUID procurementItemId,
		@NotNull @DecimalMin("0.001") BigDecimal receivedWeightKg) {
}
