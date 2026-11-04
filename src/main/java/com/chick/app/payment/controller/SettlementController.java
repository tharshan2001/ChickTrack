package com.chick.app.payment.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.payment.dto.BalanceResponse;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BranchSettlementResponse;
import com.chick.app.payment.dto.SettlementReverseRequest;
import com.chick.app.payment.service.SettlementService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1")
public class SettlementController {

	private final SettlementService service;

	public SettlementController(SettlementService service) {
		this.service = service;
	}

	@GetMapping("/branch-settlements")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<BranchSettlementResponse>>> list(
			@PageableDefault(size = 20, sort = "settlementDate") Pageable pageable,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, branchId))));
	}

	@GetMapping("/branch-settlements/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BranchSettlementResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping("/branch-settlements")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<BranchSettlementResponse>> create(@Valid @RequestBody BranchSettlementCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PostMapping("/branch-settlements/{id}/reverse")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<BranchSettlementResponse>> reverse(
			@PathVariable UUID id,
			@Valid @RequestBody SettlementReverseRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.reverse(id, request)));
	}

	@GetMapping("/branches/{id}/balance")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BalanceResponse>> branchBalance(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.branchBalance(id)));
	}

	@GetMapping("/branches/{id}/open-sales")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<java.util.List<com.chick.app.payment.dto.OpenSaleResponse>>> openSales(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.openSales(id)));
	}
}
