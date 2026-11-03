package com.chick.app.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record SettlementReverseRequest(@NotBlank String reason) {
}
