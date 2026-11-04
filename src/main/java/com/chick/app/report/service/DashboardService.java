package com.chick.app.report.service;

import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.repository.BranchSettlementRepository;
import com.chick.app.procurement.domain.Procurement;
import com.chick.app.procurement.repository.ProcurementRepository;
import com.chick.app.report.dto.DashboardResponse;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

	private static final int MONEY_SCALE = 2;
	private static final int WEIGHT_SCALE = 3;

	private final ProcurementRepository procurements;
	private final StockBatchRepository stockBatches;
	private final DistributionRepository distributions;
	private final InventoryMovementRepository movements;
	private final BranchSettlementRepository settlements;
	private final BranchSaleRepository branchSales;
	private final ReportScopeService scopeService;
	private final ReportService reports;

	public DashboardService(
			ProcurementRepository procurements,
			StockBatchRepository stockBatches,
			DistributionRepository distributions,
			InventoryMovementRepository movements,
			BranchSettlementRepository settlements,
			BranchSaleRepository branchSales,
			ReportScopeService scopeService,
			ReportService reports) {
		this.procurements = procurements;
		this.stockBatches = stockBatches;
		this.distributions = distributions;
		this.movements = movements;
		this.settlements = settlements;
		this.branchSales = branchSales;
		this.scopeService = scopeService;
		this.reports = reports;
	}

	@Transactional(readOnly = true)
	public DashboardResponse dashboard(LocalDate from, LocalDate to, UUID branchId) {
		LocalDate effectiveFrom = from != null ? from : LocalDate.now(ZoneOffset.UTC);
		LocalDate effectiveTo = to != null ? to : effectiveFrom;
		reports.validateRange(effectiveFrom, effectiveTo);
		ReportScopeService.Scope scope = scopeService.resolve(branchId);

		BigDecimal procurementValue = BigDecimal.ZERO;
		BigDecimal procurementWeight = BigDecimal.ZERO;
		Specification<Procurement> procurementSpec = (root, query, cb) -> cb.and(
				root.get("status").in(ReportService.POSTED_PROCUREMENT_STATUSES),
				cb.greaterThanOrEqualTo(root.get("procurementDate"), effectiveFrom),
				cb.lessThanOrEqualTo(root.get("procurementDate"), effectiveTo));
		for (Procurement procurement : procurements.findAll(procurementSpec)) {
			procurementValue = procurementValue.add(procurement.getTotalCost());
			procurementWeight = procurementWeight.add(procurement.getPaidWeightKg());
		}

		Instant stockCutoff = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		BigDecimal stockWeight = BigDecimal.ZERO;
		BigDecimal stockValue = BigDecimal.ZERO;
		for (StockBatch batch : stockBatches.findAll((root, query, cb) -> cb.lessThan(root.get("receivedAt"), stockCutoff))) {
			stockWeight = stockWeight.add(batch.getRemainingWeightKg());
			stockValue = stockValue.add(batch.getRemainingWeightKg().multiply(batch.getLandedCostPerKg()));
		}

		BigDecimal distributionWeight = BigDecimal.ZERO;
		BigDecimal distributionTransferValue = BigDecimal.ZERO;
		BigDecimal ourMargin = BigDecimal.ZERO;
		BigDecimal outstandingTransferValue = BigDecimal.ZERO;
		BigDecimal outstandingSettled = BigDecimal.ZERO;

		Specification<Distribution> distributionSpec = (root, query, cb) -> cb.and(
				root.get("status").in(ReportService.POSTED_DISTRIBUTION_STATUSES),
				cb.greaterThanOrEqualTo(root.get("distributionDate"), effectiveFrom),
				cb.lessThanOrEqualTo(root.get("distributionDate"), effectiveTo));
		if (branchId != null) {
			distributionSpec = distributionSpec.and(
					(root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		for (Distribution distribution : distributions.findAll(distributionSpec)) {
			if (!scope.allows(distribution.getBranch().getId())) {
				continue;
			}
			distributionWeight = distributionWeight.add(distribution.getTotalWeightKg());
			distributionTransferValue = distributionTransferValue.add(distribution.getTotalTransferValue());
			ourMargin = ourMargin.add(distribution.getTotalOurMargin());
		}

		Specification<BranchSale> allSalesSpec = (root, query, cb) -> cb.conjunction();
		if (branchId != null) {
			allSalesSpec = allSalesSpec.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		for (BranchSale sale : branchSales.findAll(allSalesSpec)) {
			if (scope.allows(sale.getBranch().getId())) {
				outstandingTransferValue = outstandingTransferValue.add(sale.getTransferValue());
			}
		}

		Specification<BranchSettlement> settlementSpec =
				(root, query, cb) -> cb.and(cb.equal(root.get("status"), BranchSettlement.Status.POSTED));
		for (BranchSettlement settlement : settlements.findAll(settlementSpec)) {
			if (!scope.allows(settlement.getBranch().getId())) {
				continue;
			}
			outstandingSettled = outstandingSettled.add(settlement.getAllocations().stream()
					.map(allocation -> allocation.getAmount())
					.reduce(BigDecimal.ZERO, BigDecimal::add));
		}

		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal salesTransferValue = BigDecimal.ZERO;
		Specification<BranchSale> salesSpec = (root, query, cb) -> cb.and(
				cb.greaterThanOrEqualTo(root.get("saleDate"), effectiveFrom),
				cb.lessThanOrEqualTo(root.get("saleDate"), effectiveTo));
		if (branchId != null) {
			salesSpec = salesSpec.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		for (BranchSale sale : branchSales.findAll(salesSpec)) {
			if (!scope.allows(sale.getBranch().getId())) {
				continue;
			}
			totalSales = totalSales.add(sale.getTotalSales());
			salesTransferValue = salesTransferValue.add(sale.getTransferValue());
		}
		BigDecimal branchProfit = totalSales.subtract(salesTransferValue);

		Instant shrinkageStart = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant shrinkageEnd = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		BigDecimal shrinkageWeight = BigDecimal.ZERO;
		Specification<InventoryMovement> shrinkageSpec = (root, query, cb) -> cb.and(
				root.get("type").in(ReportService.SHRINKAGE_MOVEMENT_TYPES),
				cb.greaterThanOrEqualTo(root.get("createdAt"), shrinkageStart),
				cb.lessThan(root.get("createdAt"), shrinkageEnd));
		for (InventoryMovement movement : movements.findAll(shrinkageSpec)) {
			Distribution distribution = "DISTRIBUTION".equals(movement.getReferenceType())
					&& movement.getReferenceId() != null
							? distributions.findById(movement.getReferenceId()).orElse(null)
							: null;
			UUID movementBranchId = distribution != null ? distribution.getBranch().getId() : null;
			if (!scope.allows(movementBranchId)) {
				continue;
			}
			shrinkageWeight = shrinkageWeight.add(movement.getWeightKg());
		}

		return new DashboardResponse(
				effectiveFrom,
				effectiveTo,
				procurementValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				procurementWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
				stockWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
				stockValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				distributionWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
				distributionTransferValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				ourMargin.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				totalSales.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				branchProfit.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				outstandingTransferValue.subtract(outstandingSettled).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
				shrinkageWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
	}
}
