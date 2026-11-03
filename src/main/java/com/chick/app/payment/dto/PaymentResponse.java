package com.chick.app.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
		UUID id,
		UUID supplierId,
		LocalDate paymentDate,
		BigDecimal amount,
		String method,
		String reference,
		String notes,
		String status,
		String reversalReason,
		List<PaymentAllocationResponse> allocations,
		Instant createdAt,
		Instant updatedAt) {
}
