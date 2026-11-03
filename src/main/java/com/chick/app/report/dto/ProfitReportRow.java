package com.chick.app.report.dto;

import java.math.BigDecimal;

public record ProfitReportRow(
		String branchName,
		int saleCount,
		BigDecimal totalSales,
		BigDecimal costAmount,
		BigDecimal profit,
		BigDecimal profitPercent) {
}
