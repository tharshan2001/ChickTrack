package com.chick.app.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A received delivery the branch has not fully paid for: what it owes for it and what is left. */
public record OpenDeliveryResponse(
		UUID distributionId,
		String reference,
		Instant receivedAt,
		BigDecimal owedAmount,
		BigDecimal outstandingAmount) {
}
