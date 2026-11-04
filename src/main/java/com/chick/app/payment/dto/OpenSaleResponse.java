package com.chick.app.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A branch sale that is not fully paid for yet: what it owed head office and what is left. */
public record OpenSaleResponse(
		UUID branchSaleId,
		String reference,
		LocalDate saleDate,
		BigDecimal transferValue,
		BigDecimal outstandingAmount) {
}
