package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockReportRow(
		String batchId,
		String chickenTypeName,
		LocalDate receivedDate,
		int ageDays,
		BigDecimal receivedWeightKg,
		BigDecimal remainingWeightKg,
		BigDecimal landedCostPerKg,
		BigDecimal stockValue) {
}
