package com.chick.app.branch.dto;

import java.time.Instant;
import java.util.UUID;

public record BranchResponse(
		UUID id,
		String code,
		String name,
		String address,
		String phone,
		boolean active,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
