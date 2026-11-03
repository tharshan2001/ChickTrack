package com.chick.app.report.dto;

import java.math.BigDecimal;

public record ProfitReportRow(
		String branchName,
		int distributionCount,
		BigDecimal weightKg,
		BigDecimal revenue,
		BigDecimal cost,
		BigDecimal expenseAmount,
		BigDecimal margin,
		BigDecimal marginPercent) {
}
