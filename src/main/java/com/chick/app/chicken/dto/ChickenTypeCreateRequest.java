package com.chick.app.chicken.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChickenTypeCreateRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 255) String description) {
}
