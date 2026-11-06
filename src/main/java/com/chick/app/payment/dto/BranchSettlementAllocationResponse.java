package com.chick.app.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchSettlementAllocationResponse(
		UUID id,
		UUID distributionId,
		BigDecimal amount) {
}
