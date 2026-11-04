package com.chick.app.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Weight a branch still holds of one chicken type: received from the warehouse minus sold. */
public record BranchStockResponse(UUID chickenTypeId, BigDecimal availableWeightKg) {
}
