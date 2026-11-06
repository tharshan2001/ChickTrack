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

	/** Receipt lines with a discrepancy, received in {@code [start, end)}, with their line and batch. */
	@org.springframework.data.jpa.repository.Query("""
			select item from BranchReceiptItem item
			join fetch item.branchReceipt receipt
			join fetch receipt.distribution distribution
			join fetch item.distributionItem line
			join fetch line.stockBatch batch
			where item.discrepancyWeightKg > 0
			  and receipt.receivedAt >= :start and receipt.receivedAt < :end
			""")
	java.util.List<com.chick.app.distribution.domain.BranchReceiptItem> discrepancies(
			@org.springframework.data.repository.query.Param("start") java.time.Instant start,
			@org.springframework.data.repository.query.Param("end") java.time.Instant end);

	/**
	 * What a branch owes per received delivery: rows of (distribution id, reference, last received at,
	 * received weight x transfer price), oldest first.
	 */
	@org.springframework.data.jpa.repository.Query("""
			select distribution.id, distribution.reference, max(receipt.receivedAt),
				coalesce(sum(item.receivedWeightKg * line.transferPricePerKg), 0)
			from BranchReceiptItem item
			join item.branchReceipt receipt
			join receipt.distribution distribution
			join item.distributionItem line
			where distribution.branch.id = :branchId and distribution.status in :statuses
			group by distribution.id, distribution.reference
			order by max(receipt.receivedAt), distribution.reference
			""")
	java.util.List<Object[]> receivedValueByDistribution(
			@org.springframework.data.repository.query.Param("branchId") UUID branchId,
			@org.springframework.data.repository.query.Param("statuses")
			java.util.Collection<com.chick.app.distribution.domain.DistributionStatus> statuses);

	/** Received weight x transfer price of one distribution. */
	@org.springframework.data.jpa.repository.Query("""
			select coalesce(sum(item.receivedWeightKg * line.transferPricePerKg), 0)
			from BranchReceiptItem item
			join item.branchReceipt receipt
			join item.distributionItem line
			where receipt.distribution.id = :distributionId
			""")
	java.math.BigDecimal receivedValue(
			@org.springframework.data.repository.query.Param("distributionId") UUID distributionId);
}
