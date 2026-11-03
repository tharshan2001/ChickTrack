package com.chick.app.chicken.controller;

import com.chick.app.chicken.dto.ChickenTypeCreateRequest;
import com.chick.app.chicken.dto.ChickenTypeResponse;
import com.chick.app.chicken.dto.ChickenTypeStatusRequest;
import com.chick.app.chicken.dto.ChickenTypeUpdateRequest;
import com.chick.app.chicken.service.ChickenTypeService;
import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
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
@RequestMapping("/api/v1/chicken-types")
public class ChickenTypeController {

	private final ChickenTypeService service;

	public ChickenTypeController(ChickenTypeService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<ChickenTypeResponse>>> list(
			@PageableDefault(size = 20, sort = "name") Pageable pageable,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, active, search))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<ChickenTypeResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<ChickenTypeResponse>> create(@Valid @RequestBody ChickenTypeCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<ChickenTypeResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody ChickenTypeUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<ChickenTypeResponse>> setStatus(
			@PathVariable UUID id,
			@Valid @RequestBody ChickenTypeStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setStatus(id, request)));
	}
}
