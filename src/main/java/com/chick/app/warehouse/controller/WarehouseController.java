package com.chick.app.warehouse.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.warehouse.dto.WarehouseCreateRequest;
import com.chick.app.warehouse.dto.WarehouseResponse;
import com.chick.app.warehouse.dto.WarehouseStatusRequest;
import com.chick.app.warehouse.dto.WarehouseUpdateRequest;
import com.chick.app.warehouse.service.WarehouseService;
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
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

	private final WarehouseService service;

	public WarehouseController(WarehouseService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<WarehouseResponse>>> list(
			@PageableDefault(size = 20, sort = "name") Pageable pageable,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, active, search))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<WarehouseResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('WAREHOUSE_MANAGER')")
	public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody WarehouseCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('OWNER') or hasRole('WAREHOUSE_MANAGER')")
	public ResponseEntity<ApiResponse<WarehouseResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody WarehouseUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER') or hasRole('WAREHOUSE_MANAGER')")
	public ResponseEntity<ApiResponse<WarehouseResponse>> setStatus(
			@PathVariable UUID id,
			@Valid @RequestBody WarehouseStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setStatus(id, request)));
	}
}
