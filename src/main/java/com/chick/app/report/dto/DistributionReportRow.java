package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DistributionReportRow(
		String reference,
		LocalDate distributionDate,
		String branchName,
		String status,
		BigDecimal weightKg,
		BigDecimal cost,
		BigDecimal transferValue,
		BigDecimal ourMargin,
		BigDecimal expenseAmount) {
}
