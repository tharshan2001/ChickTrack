package com.chick.app.inventory.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.dto.InventoryMovementResponse;
import com.chick.app.inventory.dto.StockAdjustmentRequest;
import com.chick.app.inventory.dto.StockAdjustmentResponse;
import com.chick.app.inventory.dto.StockBatchResponse;
import com.chick.app.inventory.service.InventoryService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Warehouse stock: batches, the movement ledger and stock adjustments. */
@RestController
@RequestMapping("/api/v1")
public class InventoryController {

	private static final String READ =
			"hasAnyRole('OWNER','WAREHOUSE_MANAGER','DISTRIBUTION_MANAGER','PROCUREMENT_MANAGER','ACCOUNTANT')";
	private static final String WRITE = "hasAnyRole('OWNER','WAREHOUSE_MANAGER')";

	private final InventoryService service;

	public InventoryController(InventoryService service) {
		this.service = service;
	}

	@GetMapping("/stock-batches")
	@PreAuthorize(READ)
	public ResponseEntity<ApiResponse<PageResponse<StockBatchResponse>>> batches(
			@PageableDefault(size = 20, sort = "receivedAt") Pageable pageable,
			@RequestParam(required = false) UUID chickenTypeId,
			@RequestParam(required = false) Boolean available) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.listBatches(pageable, chickenTypeId, available))));
	}

	@GetMapping("/stock-batches/{id}")
	@PreAuthorize(READ)
	public ResponseEntity<ApiResponse<StockBatchResponse>> batch(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.getBatch(id)));
	}

	@GetMapping("/inventory-movements")
	@PreAuthorize(READ)
	public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> movements(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			@RequestParam(required = false) UUID batchId,
			@RequestParam(required = false) InventoryMovement.MovementType type,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.listMovements(pageable, batchId, type, from, to))));
	}

	@GetMapping("/stock-adjustments")
	@PreAuthorize(READ)
	public ResponseEntity<ApiResponse<PageResponse<StockAdjustmentResponse>>> adjustments(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
			@RequestParam(required = false) UUID batchId) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.listAdjustments(pageable, batchId))));
	}

	@PostMapping("/stock-adjustments")
	@PreAuthorize(WRITE)
	public ResponseEntity<ApiResponse<StockAdjustmentResponse>> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.adjust(request)));
	}
}
