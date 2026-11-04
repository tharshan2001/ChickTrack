package com.chick.app.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UserCreateRequest(
		@NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
				message = "may contain letters, digits, dot, underscore and hyphen") String username,
		@NotBlank @Size(min = 8, max = 128) String password,
		@NotBlank @Size(max = 150) String fullName,
		@Email @Size(max = 150) String email,
		@Size(max = 32) String phone,
		@NotEmpty Set<@NotBlank String> roles,
		Set<UUID> branchIds) {
}
