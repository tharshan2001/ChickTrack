package com.chick.app.chicken.dto;

import java.time.Instant;
import java.util.UUID;

public record ChickenTypeResponse(
		UUID id,
		String name,
		String description,
		boolean active,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
