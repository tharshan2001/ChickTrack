package com.chick.app.margin.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.margin.dto.CompanyMarginRuleCreateRequest;
import com.chick.app.margin.dto.CompanyMarginRuleResponse;
import com.chick.app.margin.service.CompanyMarginRuleService;
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
@RequestMapping("/api/v1/company-margin-rules")
public class CompanyMarginRuleController {

	private final CompanyMarginRuleService service;

	public CompanyMarginRuleController(CompanyMarginRuleService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<CompanyMarginRuleResponse>>> list(
			@PageableDefault(size = 20, sort = "effectiveFrom") Pageable pageable,
			@RequestParam(required = false) UUID chickenTypeId,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(
				PageResponse.from(service.list(pageable, chickenTypeId, branchId))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<CompanyMarginRuleResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
	public ResponseEntity<ApiResponse<CompanyMarginRuleResponse>> create(
			@Valid @RequestBody CompanyMarginRuleCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}
}
