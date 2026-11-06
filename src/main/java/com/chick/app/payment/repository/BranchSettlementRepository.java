package com.chick.app.payment.repository;

import com.chick.app.payment.domain.BranchSettlement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BranchSettlementRepository extends JpaRepository<BranchSettlement, UUID>, JpaSpecificationExecutor<BranchSettlement> {

	@org.springframework.data.jpa.repository.Query("""
			select coalesce(sum(a.amount), 0) from BranchSettlementAllocation a
			where a.distribution.id = :distributionId and a.branchSettlement.status = :status
			""")
	java.math.BigDecimal allocatedToDistribution(
			@org.springframework.data.repository.query.Param("distributionId") UUID distributionId,
			@org.springframework.data.repository.query.Param("status") BranchSettlement.Status status);

	@org.springframework.data.jpa.repository.Query("""
			select coalesce(sum(a.amount), 0) from BranchSettlementAllocation a
			where a.branchSettlement.branch.id = :branchId and a.branchSettlement.status = :status
			""")
	java.math.BigDecimal allocatedForBranch(
			@org.springframework.data.repository.query.Param("branchId") UUID branchId,
			@org.springframework.data.repository.query.Param("status") BranchSettlement.Status status);

	/** Posted allocations per delivery for a branch: rows of (distribution id, amount). */
	@org.springframework.data.jpa.repository.Query("""
			select a.distribution.id, coalesce(sum(a.amount), 0) from BranchSettlementAllocation a
			where a.branchSettlement.branch.id = :branchId and a.branchSettlement.status = :status
			group by a.distribution.id
			""")
	java.util.List<Object[]> allocatedByDistribution(
			@org.springframework.data.repository.query.Param("branchId") UUID branchId,
			@org.springframework.data.repository.query.Param("status") BranchSettlement.Status status);
}
