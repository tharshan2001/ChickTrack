package com.chick.app.procurement.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.procurement.domain.ProcurementStatus;
import com.chick.app.procurement.dto.ProcurementCreateRequest;
import com.chick.app.procurement.dto.ProcurementResponse;
import com.chick.app.procurement.service.ProcurementService;
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
@RequestMapping("/api/v1/procurements")
public class ProcurementController {

	private final ProcurementService service;

	public ProcurementController(ProcurementService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<PageResponse<ProcurementResponse>>> list(
			@PageableDefault(size = 20, sort = "procurementDate") Pageable pageable,
			@RequestParam(required = false) UUID supplierId,
			@RequestParam(required = false) ProcurementStatus status,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, supplierId, status, from, to))));
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<ProcurementResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping
	@PreAuthorize("hasRole('OWNER') or hasRole('PROCUREMENT_MANAGER')")
	public ResponseEntity<ApiResponse<ProcurementResponse>> create(@Valid @RequestBody ProcurementCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}
}
