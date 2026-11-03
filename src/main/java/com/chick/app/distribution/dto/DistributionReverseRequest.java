package com.chick.app.distribution.dto;

import jakarta.validation.constraints.NotBlank;

public record DistributionReverseRequest(@NotBlank String reason) {
}
