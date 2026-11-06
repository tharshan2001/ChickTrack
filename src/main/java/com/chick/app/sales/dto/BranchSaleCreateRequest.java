package com.chick.app.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Day close: what the branch took in that day and the stock it has left per chicken type. */
public record BranchSaleCreateRequest(
		@NotNull UUID branchId,
		@NotNull LocalDate saleDate,
		@NotNull @DecimalMin("0") BigDecimal totalSales,
		String notes,
		@NotEmpty List<@Valid BranchSaleItemRequest> items) {
}
