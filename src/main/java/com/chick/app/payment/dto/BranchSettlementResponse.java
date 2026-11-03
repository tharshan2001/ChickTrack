package com.chick.app.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BranchSettlementResponse(
		UUID id,
		UUID branchId,
		LocalDate settlementDate,
		BigDecimal amount,
		String method,
		String reference,
		String notes,
		String status,
		String reversalReason,
		List<BranchSettlementAllocationResponse> allocations,
		Instant createdAt,
		Instant updatedAt) {
}
