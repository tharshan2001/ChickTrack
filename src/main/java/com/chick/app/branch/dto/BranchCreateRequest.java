package com.chick.app.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BranchCreateRequest(
		@NotBlank @Size(max = 32) String code,
		@NotBlank @Size(max = 150) String name,
		String address,
		@Size(max = 32) String phone) {
}
