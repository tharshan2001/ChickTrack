package com.chick.app.sales.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.sales.dto.BranchSaleCreateRequest;
import com.chick.app.sales.dto.BranchSaleResponse;
import com.chick.app.sales.service.BranchSaleService;
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
@RequestMapping("/api/v1/branch-sales")
public class BranchSaleController {

	private final BranchSaleService service;

	public BranchSaleController(BranchSaleService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<BranchSaleResponse>>> list(
			@PageableDefault(size = 20, sort = "saleDate") Pageable pageable,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, branchId, from, to))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BranchSaleResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('BRANCH_RECEIVER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<BranchSaleResponse>> record(@Valid @RequestBody BranchSaleCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.record(request)));
	}
}
