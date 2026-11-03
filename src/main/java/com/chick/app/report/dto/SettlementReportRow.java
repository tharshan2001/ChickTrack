package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementReportRow(
		String settlementId,
		LocalDate settlementDate,
		String branchName,
		String method,
		String reference,
		BigDecimal amount,
		int allocationCount) {
}
