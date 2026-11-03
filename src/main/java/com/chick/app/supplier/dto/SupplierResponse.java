package com.chick.app.supplier.dto;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
		UUID id,
		String name,
		String contactPerson,
		String phone,
		String email,
		String address,
		String paymentTerms,
		boolean active,
		long version,
		Instant createdAt,
		Instant updatedAt) {
}
