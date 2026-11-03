package com.chick.app.supplier.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.supplier.dto.SupplierCreateRequest;
import com.chick.app.supplier.dto.SupplierResponse;
import com.chick.app.supplier.dto.SupplierStatusRequest;
import com.chick.app.supplier.dto.SupplierUpdateRequest;
import com.chick.app.supplier.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

	private final SupplierService service;

	public SupplierController(SupplierService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> list(
			@PageableDefault(size = 20, sort = "name") Pageable pageable,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, active, search))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<SupplierResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody SupplierCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<SupplierResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody SupplierUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<SupplierResponse>> setStatus(
			@PathVariable UUID id,
			@Valid @RequestBody SupplierStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setStatus(id, request)));
	}
}
