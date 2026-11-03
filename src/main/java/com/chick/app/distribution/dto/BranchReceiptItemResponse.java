package com.chick.app.distribution.dto;

import com.chick.app.distribution.domain.DiscrepancyReason;
import java.math.BigDecimal;
import java.util.UUID;

public record BranchReceiptItemResponse(
		UUID id,
		UUID distributionItemId,
		BigDecimal receivedWeightKg,
		BigDecimal discrepancyWeightKg,
		DiscrepancyReason discrepancyReason) {
}
