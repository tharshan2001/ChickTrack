package com.chick.app.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BranchSaleCreateRequest(
		@NotNull UUID branchId,
		@NotNull LocalDate saleDate,
		String notes,
		@NotEmpty List<@Valid BranchSaleItemRequest> items) {
}
