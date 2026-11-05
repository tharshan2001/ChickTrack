package com.chick.app.identity.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.common.api.PageResponse;
import com.chick.app.identity.dto.PasswordChangeRequest;
import com.chick.app.identity.dto.PasswordResetRequest;
import com.chick.app.identity.dto.RoleResponse;
import com.chick.app.identity.dto.UserBranchesRequest;
import com.chick.app.identity.dto.UserCreateRequest;
import com.chick.app.identity.dto.UserCredentialsResponse;
import com.chick.app.identity.dto.UserResponse;
import com.chick.app.identity.dto.UserRolesRequest;
import com.chick.app.identity.dto.UserStatusRequest;
import com.chick.app.identity.dto.UserUpdateRequest;
import com.chick.app.identity.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1")
public class UserController {

	private final UserService service;

	public UserController(UserService service) {
		this.service = service;
	}

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<UserResponse>> me() {
		return ResponseEntity.ok(ApiResponse.of(service.me()));
	}

	@PostMapping("/me/password")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<ApiResponse<UserResponse>> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.changeOwnPassword(request)));
	}

	@GetMapping("/roles")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<List<RoleResponse>>> roles() {
		return ResponseEntity.ok(ApiResponse.of(service.roles()));
	}

	@GetMapping("/users")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
			@PageableDefault(size = 20, sort = "username") Pageable pageable,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String role,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) String search) {
		return ResponseEntity.ok(ApiResponse.of(PageResponse.from(service.list(pageable, active, role, branchId, search))));
	}

	@GetMapping("/users/{id}")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.of(service.get(id)));
	}

	@PostMapping("/users")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserCredentialsResponse>> create(@Valid @RequestBody UserCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request)));
	}

	@PutMapping("/users/{id}")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.update(id, request)));
	}

	@PutMapping("/users/{id}/roles")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserResponse>> setRoles(@PathVariable UUID id, @Valid @RequestBody UserRolesRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setRoles(id, request)));
	}

	@PutMapping("/users/{id}/branches")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserResponse>> setBranches(
			@PathVariable UUID id,
			@Valid @RequestBody UserBranchesRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setBranches(id, request)));
	}

	@PatchMapping("/users/{id}/status")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserResponse>> setStatus(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request) {
		return ResponseEntity.ok(ApiResponse.of(service.setStatus(id, request)));
	}

	@PostMapping("/users/{id}/password")
	@PreAuthorize("hasRole('OWNER')")
	public ResponseEntity<ApiResponse<UserCredentialsResponse>> resetPassword(
			@PathVariable UUID id,
			@Valid @RequestBody(required = false) PasswordResetRequest request) {
		PasswordResetRequest body = request == null ? new PasswordResetRequest(null, null) : request;
		return ResponseEntity.ok(ApiResponse.of(service.resetPassword(id, body)));
	}
}
