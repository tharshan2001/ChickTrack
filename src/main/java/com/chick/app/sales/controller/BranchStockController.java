package com.chick.app.sales.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.sales.dto.BranchStockResponse;
import com.chick.app.sales.service.BranchSaleService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchStockController {

	private final BranchSaleService service;

	public BranchStockController(BranchSaleService service) {
		this.service = service;
	}

	/**
	 * Weight on hand at the branch per chicken type. With {@code date}, the stock before that day
	 * closes (the opening stock of the day close form).
	 */
	@GetMapping("/{id}/stock")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<List<BranchStockResponse>>> stock(
			@PathVariable UUID id,
			@RequestParam(required = false) java.time.LocalDate date) {
		return ResponseEntity.ok(ApiResponse.of(service.stock(id, date)));
	}
}
