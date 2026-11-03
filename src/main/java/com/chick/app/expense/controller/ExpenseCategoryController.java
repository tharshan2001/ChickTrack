package com.chick.app.expense.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.expense.dto.ExpenseCategoryCreateRequest;
import com.chick.app.expense.dto.ExpenseCategoryResponse;
import com.chick.app.expense.dto.ExpenseCategoryStatusRequest;
import com.chick.app.expense.dto.ExpenseCategoryUpdateRequest;
import com.chick.app.expense.service.ExpenseCategoryService;
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
@RequestMapping("/api/v1/expense-categories")
public class ExpenseCategoryController {

	private final ExpenseCategoryService service;

	public ExpenseCategoryController(ExpenseCategoryService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<ExpenseCategoryResponse>>> list(
			@PageableDefault(size = 20, sort = "name") Pageable pageable,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, active, search))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> create(@Valid @RequestBody ExpenseCategoryCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody ExpenseCategoryUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> setStatus(
			@PathVariable UUID id,
			@Valid @RequestBody ExpenseCategoryStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setStatus(id, request)));
	}
}
