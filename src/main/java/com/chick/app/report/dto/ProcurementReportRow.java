package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcurementReportRow(
		String reference,
		LocalDate procurementDate,
		String supplierName,
		int itemCount,
		BigDecimal paidWeightKg,
		BigDecimal purchaseValue,
		BigDecimal commissionAmount,
		BigDecimal expenseAmount,
		BigDecimal totalCost,
		BigDecimal landedCostPerKg) {
}
