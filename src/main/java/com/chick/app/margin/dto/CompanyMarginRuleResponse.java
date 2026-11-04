package com.chick.app.margin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompanyMarginRuleResponse(
		UUID id,
		UUID chickenTypeId,
		UUID branchId,
		BigDecimal markupPercent,
		Instant effectiveFrom,
		Instant createdAt) {
}
