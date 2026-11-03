package com.chick.app.report.service;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.identity.repository.UserAccountRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ReportScopeService {

	private final UserAccountRepository users;

	public ReportScopeService(UserAccountRepository users) {
		this.users = users;
	}

	public Scope resolve(UUID requestedBranchId) {
		Set<UUID> allowed = allowedBranches();
		if (requestedBranchId != null && !allowed.isEmpty() && !allowed.contains(requestedBranchId)) {
			throw new BusinessException("FORBIDDEN",
					"Branch is outside the authenticated user's scope", HttpStatus.FORBIDDEN);
		}
		return new Scope(allowed, requestedBranchId);
	}

	private Set<UUID> allowedBranches() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Set.of();
		}
		return users.findByUsername(authentication.getName())
				.map(user -> user.getBranches().stream()
						.map(Branch::getId)
						.collect(Collectors.toCollection(LinkedHashSet::new)))
				.map(Set::copyOf)
				.orElseGet(Set::of);
	}

	public record Scope(Set<UUID> allowedBranches, UUID requestedBranchId) {

		public Scope {
			allowedBranches = Set.copyOf(allowedBranches);
		}

		public boolean allows(UUID branchId) {
			if (branchId == null) {
				return true;
			}
			if (requestedBranchId != null) {
				return requestedBranchId.equals(branchId);
			}
			return allowedBranches.isEmpty() || allowedBranches.contains(branchId);
		}

		public boolean restricted() {
			return !allowedBranches.isEmpty();
		}
	}
}
