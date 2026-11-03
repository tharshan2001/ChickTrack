package com.chick.app.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAllocationRequest(
		@NotNull UUID procurementId,
		@NotNull @DecimalMin("0.01") BigDecimal amount) {
}
