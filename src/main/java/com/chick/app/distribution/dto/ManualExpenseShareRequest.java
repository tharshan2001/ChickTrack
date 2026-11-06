package com.chick.app.distribution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** The part of a MANUAL distribution expense one chicken type carries. */
public record ManualExpenseShareRequest(
		@NotNull UUID chickenTypeId,
		@NotNull @DecimalMin("0") BigDecimal amount) {
}
