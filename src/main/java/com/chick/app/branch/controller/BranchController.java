package com.chick.app.branch.controller;

import com.chick.app.branch.dto.BranchCreateRequest;
import com.chick.app.branch.dto.BranchResponse;
import com.chick.app.branch.dto.BranchStatusRequest;
import com.chick.app.branch.dto.BranchUpdateRequest;
import com.chick.app.branch.service.BranchService;
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
@RequestMapping("/api/v1/branches")
public class BranchController {

	private final BranchService service;

	public BranchController(BranchService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<BranchResponse>>> list(
			@PageableDefault(size = 20, sort = "name") Pageable pageable,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, active, search))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BranchResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<BranchResponse>> create(@Valid @RequestBody BranchCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<BranchResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody BranchUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<BranchResponse>> setStatus(
			@PathVariable UUID id,
			@Valid @RequestBody BranchStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setStatus(id, request)));
	}
}
