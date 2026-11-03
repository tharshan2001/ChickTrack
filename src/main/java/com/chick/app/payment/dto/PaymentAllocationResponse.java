package com.chick.app.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAllocationResponse(
		UUID id,
		UUID procurementId,
		BigDecimal amount) {
}
