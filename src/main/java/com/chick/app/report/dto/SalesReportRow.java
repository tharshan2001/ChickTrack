package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesReportRow(
		String reference,
		LocalDate saleDate,
		String branchName,
		BigDecimal totalSales,
		BigDecimal expectedSales,
		BigDecimal variance,
		BigDecimal transferValue,
		BigDecimal branchProfit,
		BigDecimal profitPercent) {
}
