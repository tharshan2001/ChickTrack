package com.chick.app.payment.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.payment.dto.BalanceResponse;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BranchSettlementResponse;
import com.chick.app.payment.dto.PaymentCreateRequest;
import com.chick.app.payment.dto.PaymentResponse;
import com.chick.app.payment.dto.PaymentReverseRequest;
import com.chick.app.payment.service.PaymentService;
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
public class PaymentController {

	private final PaymentService service;

	public PaymentController(PaymentService service) {
		this.service = service;
	}

	@GetMapping("/payments")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> listPayments(
			@PageableDefault(size = 20, sort = "paymentDate") Pageable pageable,
			@RequestParam(required = false) UUID supplierId) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.listPayments(pageable, supplierId))));
	}

	@GetMapping("/payments/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.getPayment(id)));
	}

	@PostMapping("/payments")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(@Valid @RequestBody PaymentCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.createPayment(request)));
	}

	@PostMapping("/payments/{id}/reverse")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<PaymentResponse>> reversePayment(
			@PathVariable UUID id,
			@Valid @RequestBody PaymentReverseRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.reversePayment(id, request)));
	}

	@GetMapping("/suppliers/{id}/balance")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BalanceResponse>> supplierBalance(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.supplierBalance(id)));
	}

	@GetMapping("/branch-settlements")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<BranchSettlementResponse>>> listSettlements(
			@PageableDefault(size = 20, sort = "settlementDate") Pageable pageable,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.listSettlements(pageable, branchId))));
	}

	@GetMapping("/branch-settlements/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BranchSettlementResponse>> getSettlement(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.getSettlement(id)));
	}

	@PostMapping("/branch-settlements")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<BranchSettlementResponse>> createSettlement(
			@Valid @RequestBody BranchSettlementCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.createSettlement(request)));
	}

	@PostMapping("/branch-settlements/{id}/reverse")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<BranchSettlementResponse>> reverseSettlement(
			@PathVariable UUID id,
			@Valid @RequestBody PaymentReverseRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.reverseSettlement(id, request)));
	}

	@GetMapping("/branches/{id}/balance")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<BalanceResponse>> branchBalance(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.branchBalance(id)));
	}
}
