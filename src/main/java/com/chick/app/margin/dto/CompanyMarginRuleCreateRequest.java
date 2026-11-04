package com.chick.app.margin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompanyMarginRuleCreateRequest(
		@NotNull UUID chickenTypeId,
		UUID branchId,
		@NotNull @DecimalMin("0.0000") BigDecimal markupPercent,
		@NotNull Instant effectiveFrom) {
}
