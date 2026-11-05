package com.chick.app.expense.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.expense.dto.OperatingExpenseRequest;
import com.chick.app.expense.dto.OperatingExpenseResponse;
import com.chick.app.expense.dto.OperatingExpenseVoidRequest;
import com.chick.app.expense.service.OperatingExpenseService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expenses")
@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
public class OperatingExpenseController {

	private final OperatingExpenseService service;

	public OperatingExpenseController(OperatingExpenseService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<PageResponse<OperatingExpenseResponse>>> list(
			@PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) Boolean headOffice,
			@RequestParam(required = false) String status) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(
				service.list(pageable, from, to, categoryId, branchId, headOffice, status))));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<OperatingExpenseResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<OperatingExpenseResponse>> create(@Valid @RequestBody OperatingExpenseRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<OperatingExpenseResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody OperatingExpenseRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PostMapping("/{id}/void")
	public ResponseEntity<ApiResponse<OperatingExpenseResponse>> voidExpense(
			@PathVariable UUID id,
			@Valid @RequestBody OperatingExpenseVoidRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.voidExpense(id, request)));
	}
}
