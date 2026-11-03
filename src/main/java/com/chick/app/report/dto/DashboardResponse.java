package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardResponse(
		LocalDate from,
		LocalDate to,
		BigDecimal procurementValue,
		BigDecimal procurementWeightKg,
		BigDecimal stockWeightKg,
		BigDecimal stockValue,
		BigDecimal distributionWeightKg,
		BigDecimal distributionRevenue,
		BigDecimal branchOutstanding,
		BigDecimal shrinkageWeightKg) {
}
