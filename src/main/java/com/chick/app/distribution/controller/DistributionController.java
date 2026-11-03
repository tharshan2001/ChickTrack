package com.chick.app.distribution.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.dto.BranchReceiptRequest;
import com.chick.app.distribution.dto.BranchReceiptResponse;
import com.chick.app.distribution.dto.DistributionCreateRequest;
import com.chick.app.distribution.dto.DistributionResponse;
import com.chick.app.distribution.dto.DistributionReverseRequest;
import com.chick.app.distribution.service.DistributionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/distributions")
public class DistributionController {

	private final DistributionService service;

	public DistributionController(DistributionService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<DistributionResponse>>> list(
			@PageableDefault(size = 20, sort = "distributionDate") Pageable pageable,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) DistributionStatus status,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ResponseEntity.ok(ApiResponse.of(service.list(pageable, branchId, status, from, to)));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<DistributionResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('DISTRIBUTION_MANAGER')")
	public ResponseEntity<ApiResponse<DistributionResponse>> create(@Valid @RequestBody DistributionCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PostMapping("/{id}/dispatch")
	@PreAuthorize("hasRole('OWNER') or hasRole('DISTRIBUTION_MANAGER')")
	public ResponseEntity<ApiResponse<DistributionResponse>> dispatch(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.dispatch(id)));
	}

	@PostMapping("/{id}/receipts")
	@PreAuthorize("hasRole('OWNER') or hasRole('BRANCH_RECEIVER')")
	public ResponseEntity<ApiResponse<BranchReceiptResponse>> receive(
			@PathVariable UUID id,
			@Valid @RequestBody BranchReceiptRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.receive(id, request)));
	}

	@PostMapping("/{id}/reverse")
	@PreAuthorize("hasRole('OWNER') or hasRole('DISTRIBUTION_MANAGER')")
	public ResponseEntity<ApiResponse<DistributionResponse>> reverse(
			@PathVariable UUID id,
			@Valid @RequestBody DistributionReverseRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.reverse(id, request)));
	}
}
