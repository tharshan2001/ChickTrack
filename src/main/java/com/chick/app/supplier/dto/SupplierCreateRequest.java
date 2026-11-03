package com.chick.app.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierCreateRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 150) String contactPerson,
		@Size(max = 32) String phone,
		@Email @Size(max = 150) String email,
		String address,
		@Size(max = 255) String paymentTerms) {
}
