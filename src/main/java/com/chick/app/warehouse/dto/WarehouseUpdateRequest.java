package com.chick.app.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseUpdateRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 255) String location) {
}
