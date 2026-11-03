package com.chick.app.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentCreateRequest(
		@NotNull UUID supplierId,
		@NotNull LocalDate paymentDate,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotNull String method,
		String reference,
		String notes,
		@NotEmpty List<@Valid PaymentAllocationRequest> allocations) {
}
