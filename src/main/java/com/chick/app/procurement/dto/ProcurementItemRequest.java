package com.chick.app.procurement.dto;

import com.chick.app.procurement.domain.CommissionMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProcurementItemRequest(
		@NotNull UUID chickenTypeId,
		@NotNull @DecimalMin("0.001") BigDecimal paidWeightKg,
		@NotNull @DecimalMin("0.0000") BigDecimal purchasePricePerKg,
		@NotNull CommissionMethod commissionMethod,
		@DecimalMin("0.0000") BigDecimal commissionValue,
		Integer quantity) {
}
