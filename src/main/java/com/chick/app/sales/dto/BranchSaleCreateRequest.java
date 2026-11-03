package com.chick.app.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BranchSaleCreateRequest(
		@NotNull UUID branchId,
		@NotNull LocalDate saleDate,
		@NotNull @DecimalMin("0.01") BigDecimal totalSales,
		String notes) {
}
