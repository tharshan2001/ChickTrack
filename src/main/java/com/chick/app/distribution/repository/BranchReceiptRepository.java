package com.chick.app.distribution.repository;

import com.chick.app.distribution.domain.BranchReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchReceiptRepository extends JpaRepository<BranchReceipt, UUID> {

	/** Received lines of a branch's distributions in the given statuses, oldest first. */
	@org.springframework.data.jpa.repository.Query("""
			select new com.chick.app.distribution.repository.ReceivedLine(
				line.chickenType.id, receipt.receivedAt, item.receivedWeightKg, line.transferPricePerKg)
			from BranchReceiptItem item
			join item.branchReceipt receipt
			join receipt.distribution distribution
			join item.distributionItem line
			where distribution.branch.id = :branchId and distribution.status in :statuses
			order by receipt.receivedAt, item.id
			""")
	java.util.List<ReceivedLine> receivedLines(
			@org.springframework.data.repository.query.Param("branchId") UUID branchId,
			@org.springframework.data.repository.query.Param("statuses")
			java.util.Collection<com.chick.app.distribution.domain.DistributionStatus> statuses);
}
