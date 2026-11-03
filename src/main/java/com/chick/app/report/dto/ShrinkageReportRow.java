package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ShrinkageReportRow(
		LocalDate movementDate,
		String chickenTypeName,
		String branchName,
		BigDecimal weightKg,
		String reason,
		String referenceType,
		String referenceId) {
}
