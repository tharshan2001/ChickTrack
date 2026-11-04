package com.chick.app.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
		@NotBlank @Size(max = 150) String fullName,
		@Email @Size(max = 150) String email,
		@Size(max = 32) String phone) {
}
