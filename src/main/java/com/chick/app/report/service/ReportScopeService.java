package com.chick.app.report.service;

import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.identity.security.BranchAccessService.BranchAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReportScopeService {

	private final BranchAccessService branchAccess;

	public ReportScopeService(BranchAccessService branchAccess) {
		this.branchAccess = branchAccess;
	}

	public Scope resolve(UUID requestedBranchId) {
		BranchAccess access = branchAccess.current();
		if (requestedBranchId != null && !access.allows(requestedBranchId)) {
			throw BranchAccessService.forbidden();
		}
		return new Scope(access, requestedBranchId);
	}

	public record Scope(BranchAccess access, UUID requestedBranchId) {

		/**
		 * Whether a row belonging to {@code branchId} is visible. Rows without a branch (warehouse
		 * stock) are visible only to users with access to every branch.
		 */
		public boolean allows(UUID branchId) {
			if (branchId == null) {
				return access.all() && requestedBranchId == null;
			}
			if (requestedBranchId != null) {
				return requestedBranchId.equals(branchId);
			}
			return access.allows(branchId);
		}

		public boolean restricted() {
			return !access.all();
		}
	}
}
