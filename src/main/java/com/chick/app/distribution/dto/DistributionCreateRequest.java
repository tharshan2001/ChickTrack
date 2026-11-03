package com.chick.app.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DistributionCreateRequest(
		@NotNull UUID branchId,
		@NotNull LocalDate distributionDate,
		String notes,
		@NotEmpty List<@Valid DistributionItemRequest> items,
		List<@Valid DistributionExpenseRequest> expenses) {
}
