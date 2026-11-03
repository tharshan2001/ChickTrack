package com.chick.app.procurement.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcurementReverseRequest(@NotBlank String reason) {
}
