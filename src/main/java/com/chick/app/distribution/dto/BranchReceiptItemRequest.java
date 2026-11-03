package com.chick.app.distribution.dto;

import com.chick.app.distribution.domain.DiscrepancyReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record BranchReceiptItemRequest(
		@NotNull UUID distributionItemId,
		@NotNull @DecimalMin("0.001") BigDecimal receivedWeightKg,
		DiscrepancyReason discrepancyReason) {
}
