package com.chick.app.identity.security;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.identity.domain.Role;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.UserAccountRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which branches the authenticated user may read or change.
 *
 * <p>OWNER sees every branch. Head-office roles see every branch unless they are assigned
 * specific branches. Any other user (branch receiver, driver, viewer) sees only the branches
 * assigned to them, and a user with no assigned branches sees none.
 */
@Service
public class BranchAccessService {

	private static final Set<String> HEAD_OFFICE_ROLES = Set.of(
			"ACCOUNTANT", "PROCUREMENT_MANAGER", "WAREHOUSE_MANAGER", "DISTRIBUTION_MANAGER");

	private final UserAccountRepository users;

	public BranchAccessService(UserAccountRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public BranchAccess current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return BranchAccess.none();
		}
		return users.findByUsername(authentication.getName())
				.filter(UserAccount::isActive)
				.map(BranchAccessService::accessFor)
				.orElseGet(BranchAccess::none);
	}

	/** Throws {@code FORBIDDEN} unless the current user may access the branch. */
	public void require(UUID branchId) {
		if (!current().allows(branchId)) {
			throw forbidden();
		}
	}

	/**
	 * Restricts a query to the current user's branches. {@code branchPath} is the attribute that
	 * holds the branch, for example {@code "branch"}.
	 */
	public <T> Specification<T> restrict(String branchPath) {
		BranchAccess access = current();
		if (access.all()) {
			return (root, query, cb) -> cb.conjunction();
		}
		if (access.branchIds().isEmpty()) {
			return (root, query, cb) -> cb.disjunction();
		}
		return (root, query, cb) -> root.get(branchPath).get("id").in(access.branchIds());
	}

	public static BusinessException forbidden() {
		return new BusinessException("FORBIDDEN",
				"Branch is outside the authenticated user's scope", HttpStatus.FORBIDDEN);
	}

	static BranchAccess accessFor(UserAccount user) {
		Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
		if (roles.contains("OWNER")) {
			return BranchAccess.everything();
		}
		Set<UUID> assigned = user.getBranches().stream().map(Branch::getId).collect(Collectors.toUnmodifiableSet());
		if (assigned.isEmpty() && roles.stream().anyMatch(HEAD_OFFICE_ROLES::contains)) {
			return BranchAccess.everything();
		}
		return new BranchAccess(false, assigned);
	}

	public record BranchAccess(boolean all, Set<UUID> branchIds) {

		public BranchAccess {
			branchIds = Set.copyOf(branchIds);
		}

		public static BranchAccess everything() {
			return new BranchAccess(true, Set.of());
		}

		public static BranchAccess none() {
			return new BranchAccess(false, Set.of());
		}

		public boolean allows(UUID branchId) {
			return all || (branchId != null && branchIds.contains(branchId));
		}
	}
}
