package com.chick.app.distribution.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BranchReceiptResponse(
		UUID id,
		UUID distributionId,
		Instant receivedAt,
		String notes,
		List<BranchReceiptItemResponse> items) {
}
