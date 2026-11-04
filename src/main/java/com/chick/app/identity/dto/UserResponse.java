package com.chick.app.identity.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String username,
		String fullName,
		String email,
		String phone,
		boolean active,
		Set<String> roles,
		Set<UUID> branchIds,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
