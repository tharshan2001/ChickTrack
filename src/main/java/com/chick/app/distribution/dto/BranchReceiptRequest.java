package com.chick.app.distribution.dto;

import com.chick.app.distribution.domain.DiscrepancyReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BranchReceiptRequest(
		@NotNull Instant receivedAt,
		String notes,
		@NotEmpty List<@Valid BranchReceiptItemRequest> items) {
}
