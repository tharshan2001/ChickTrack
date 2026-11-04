package com.chick.app.identity.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.identity.domain.Role;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.dto.PasswordChangeRequest;
import com.chick.app.identity.dto.PasswordResetRequest;
import com.chick.app.identity.dto.RoleResponse;
import com.chick.app.identity.dto.UserBranchesRequest;
import com.chick.app.identity.dto.UserCreateRequest;
import com.chick.app.identity.dto.UserResponse;
import com.chick.app.identity.dto.UserRolesRequest;
import com.chick.app.identity.dto.UserStatusRequest;
import com.chick.app.identity.dto.UserUpdateRequest;
import com.chick.app.identity.mapper.UserMapper;
import com.chick.app.identity.repository.OAuthAuthorizationRevoker;
import com.chick.app.identity.repository.RoleRepository;
import com.chick.app.identity.repository.UserAccountRepository;
import com.chick.app.identity.security.CurrentUserService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private static final String OWNER = "OWNER";

	private final UserAccountRepository users;
	private final RoleRepository roles;
	private final BranchRepository branches;
	private final PasswordEncoder passwordEncoder;
	private final OAuthAuthorizationRevoker authorizations;
	private final CurrentUserService currentUser;
	private final UserMapper mapper;

	public UserService(
			UserAccountRepository users,
			RoleRepository roles,
			BranchRepository branches,
			PasswordEncoder passwordEncoder,
			OAuthAuthorizationRevoker authorizations,
			CurrentUserService currentUser,
			UserMapper mapper) {
		this.users = users;
		this.roles = roles;
		this.branches = branches;
		this.passwordEncoder = passwordEncoder;
		this.authorizations = authorizations;
		this.currentUser = currentUser;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public Page<UserResponse> list(Pageable pageable, Boolean active, String role, UUID branchId, String search) {
		Specification<UserAccount> specification = (root, query, cb) -> cb.conjunction();
		if (active != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (role != null && !role.isBlank()) {
			specification = specification.and((root, query, cb) -> {
				query.distinct(true);
				return cb.equal(root.join("roles").get("name"), role.trim().toUpperCase());
			});
		}
		if (branchId != null) {
			specification = specification.and((root, query, cb) -> {
				query.distinct(true);
				return cb.equal(root.join("branches").get("id"), branchId);
			});
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, cb) -> cb.or(
					cb.like(cb.lower(root.get("username")), pattern),
					cb.like(cb.lower(root.get("fullName")), pattern)));
		}
		return users.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public UserResponse get(UUID id) {
		return mapper.toResponse(find(id));
	}

	@Transactional(readOnly = true)
	public UserResponse me() {
		return mapper.toResponse(currentUser.currentUser());
	}

	@Transactional(readOnly = true)
	public List<RoleResponse> roles() {
		return roles.findAll().stream()
				.sorted(Comparator.comparing(Role::getName))
				.map(mapper::toResponse)
				.toList();
	}

	@Transactional
	@Audited(action = "USER_CREATE", entityType = "User")
	public UserResponse create(UserCreateRequest request) {
		String username = request.username().trim();
		if (users.existsByUsernameIgnoreCase(username)) {
			throw new BusinessException("CONFLICT", "Username already exists: " + username, HttpStatus.CONFLICT);
		}
		UserAccount user = new UserAccount();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setFullName(request.fullName().trim());
		user.setEmail(blankToNull(request.email()));
		user.setPhone(blankToNull(request.phone()));
		user.setActive(true);
		user.setRoles(resolveRoles(request.roles()));
		user.setBranches(resolveBranches(request.branchIds() == null ? Set.of() : request.branchIds()));
		return mapper.toResponse(users.save(user));
	}

	@Transactional
	@Audited(action = "USER_UPDATE", entityType = "User")
	public UserResponse update(UUID id, UserUpdateRequest request) {
		UserAccount user = find(id);
		user.setFullName(request.fullName().trim());
		user.setEmail(blankToNull(request.email()));
		user.setPhone(blankToNull(request.phone()));
		return mapper.toResponse(users.save(user));
	}

	@Transactional
	@Audited(action = "USER_ROLES", entityType = "User")
	public UserResponse setRoles(UUID id, UserRolesRequest request) {
		UserAccount user = find(id);
		Set<Role> newRoles = resolveRoles(request.roles());
		boolean losesOwner = hasRole(user, OWNER) && newRoles.stream().noneMatch(r -> OWNER.equals(r.getName()));
		if (losesOwner) {
			ensureNotSelf(user, "You cannot remove your own OWNER role");
			ensureAnotherActiveOwner(user);
		}
		user.setRoles(newRoles);
		UserResponse response = mapper.toResponse(users.save(user));
		authorizations.revokeAll(user.getUsername());
		return response;
	}

	@Transactional
	@Audited(action = "USER_BRANCHES", entityType = "User")
	public UserResponse setBranches(UUID id, UserBranchesRequest request) {
		UserAccount user = find(id);
		user.setBranches(resolveBranches(request.branchIds()));
		return mapper.toResponse(users.save(user));
	}

	@Transactional
	@Audited(action = "USER_STATUS", entityType = "User")
	public UserResponse setStatus(UUID id, UserStatusRequest request) {
		UserAccount user = find(id);
		if (!request.active() && user.isActive()) {
			ensureNotSelf(user, "You cannot deactivate your own account");
			if (hasRole(user, OWNER)) {
				ensureAnotherActiveOwner(user);
			}
		}
		user.setActive(request.active());
		UserResponse response = mapper.toResponse(users.save(user));
		if (!request.active()) {
			authorizations.revokeAll(user.getUsername());
		}
		return response;
	}

	@Transactional
	@Audited(action = "USER_PASSWORD_RESET", entityType = "User")
	public UserResponse resetPassword(UUID id, PasswordResetRequest request) {
		UserAccount user = find(id);
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		UserResponse response = mapper.toResponse(users.save(user));
		authorizations.revokeAll(user.getUsername());
		return response;
	}

	@Transactional
	@Audited(action = "USER_PASSWORD_CHANGE", entityType = "User")
	public UserResponse changeOwnPassword(PasswordChangeRequest request) {
		UserAccount user = currentUser.currentUser();
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION", "Current password is incorrect", HttpStatus.BAD_REQUEST);
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"New password must differ from the current one", HttpStatus.BAD_REQUEST);
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		UserResponse response = mapper.toResponse(users.save(user));
		authorizations.revokeAll(user.getUsername());
		return response;
	}

	private UserAccount find(UUID id) {
		return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
	}

	private Set<Role> resolveRoles(Set<String> names) {
		Set<Role> resolved = new HashSet<>();
		for (String name : names) {
			String normalized = name.trim().toUpperCase();
			resolved.add(roles.findByName(normalized).orElseThrow(() -> new BusinessException(
					"VALIDATION_FAILED", "Unknown role: " + normalized, HttpStatus.BAD_REQUEST)));
		}
		return resolved;
	}

	private Set<Branch> resolveBranches(Set<UUID> ids) {
		Set<Branch> resolved = new HashSet<>();
		for (UUID id : ids) {
			Branch branch = branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Branch", id));
			if (!branch.isActive()) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"Branch " + branch.getCode() + " is inactive", HttpStatus.BAD_REQUEST);
			}
			resolved.add(branch);
		}
		return resolved;
	}

	private void ensureNotSelf(UserAccount user, String message) {
		if (user.getId().equals(currentUser.currentUser().getId())) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.BAD_REQUEST);
		}
	}

	private void ensureAnotherActiveOwner(UserAccount user) {
		long owners = users.countActiveOwners();
		long remaining = user.isActive() ? owners - 1 : owners;
		if (remaining < 1) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"At least one active OWNER must remain", HttpStatus.BAD_REQUEST);
		}
	}

	private static boolean hasRole(UserAccount user, String role) {
		return user.getRoles().stream().anyMatch(r -> role.equals(r.getName()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
