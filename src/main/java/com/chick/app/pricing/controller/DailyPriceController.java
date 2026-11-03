package com.chick.app.pricing.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.pricing.dto.DailyPriceCreateRequest;
import com.chick.app.pricing.dto.DailyPriceResponse;
import com.chick.app.pricing.service.DailyPriceService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/daily-prices")
public class DailyPriceController {

	private final DailyPriceService service;

	public DailyPriceController(DailyPriceService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<DailyPriceResponse>>> list(
			@PageableDefault(size = 20, sort = "effectiveFrom") Pageable pageable,
			@RequestParam(required = false) UUID chickenTypeId,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID supplierId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(
				service.list(pageable, chickenTypeId, branchId, supplierId, from, to))));
	}

	@GetMapping("/current")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<DailyPriceResponse>> current(
			@RequestParam UUID chickenTypeId,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID supplierId,
			@RequestParam(required = false) Instant at) {
		return ResponseEntity.ok(ApiResponse.of(service.current(chickenTypeId, branchId, supplierId, at)));
	}

	@GetMapping("/trend")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<DailyPriceResponse>>> trend(
			@PageableDefault(size = 50, sort = "effectiveFrom") Pageable pageable,
			@RequestParam UUID chickenTypeId,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(
				service.trend(pageable, chickenTypeId, branchId, from, to))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<DailyPriceResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<DailyPriceResponse>> create(@Valid @RequestBody DailyPriceCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}
}
