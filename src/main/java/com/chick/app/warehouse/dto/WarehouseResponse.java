package com.chick.app.warehouse.dto;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
		UUID id,
		String name,
		String location,
		boolean active,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
