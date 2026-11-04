package com.chick.app.report.service;

import com.chick.app.common.exception.BusinessException;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.repository.BranchSettlementRepository;
import com.chick.app.procurement.domain.Procurement;
import com.chick.app.procurement.domain.ProcurementStatus;
import com.chick.app.procurement.repository.ProcurementRepository;
import com.chick.app.report.dto.DistributionReportRow;
import com.chick.app.report.dto.ProcurementReportRow;
import com.chick.app.report.dto.ProfitReportRow;
import com.chick.app.report.dto.ReportResult;
import com.chick.app.report.dto.SettlementReportRow;
import com.chick.app.report.dto.SalesReportRow;
import com.chick.app.report.dto.ShrinkageReportRow;
import com.chick.app.report.dto.StockReportRow;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

	static final Set<ProcurementStatus> POSTED_PROCUREMENT_STATUSES =
			EnumSet.of(ProcurementStatus.RECEIVED, ProcurementStatus.CLOSED);

	static final Set<DistributionStatus> POSTED_DISTRIBUTION_STATUSES =
			EnumSet.of(DistributionStatus.DISPATCHED, DistributionStatus.RECEIVED, DistributionStatus.CLOSED);

	static final Set<InventoryMovement.MovementType> SHRINKAGE_MOVEMENT_TYPES =
			EnumSet.of(InventoryMovement.MovementType.LOSS, InventoryMovement.MovementType.DAMAGE);

	private static final int MONEY_SCALE = 2;
	private static final int WEIGHT_SCALE = 3;
	private static final int COST_SCALE = 4;
	private static final int PERCENT_SCALE = 2;

	private final ProcurementRepository procurements;
	private final StockBatchRepository stockBatches;
	private final DistributionRepository distributions;
	private final InventoryMovementRepository movements;
	private final BranchSettlementRepository settlements;
	private final BranchSaleRepository branchSales;
	private final ReportScopeService scopeService;

	public ReportService(
			ProcurementRepository procurements,
			StockBatchRepository stockBatches,
			DistributionRepository distributions,
			InventoryMovementRepository movements,
			BranchSettlementRepository settlements,
			BranchSaleRepository branchSales,
			ReportScopeService scopeService) {
		this.procurements = procurements;
		this.stockBatches = stockBatches;
		this.distributions = distributions;
		this.movements = movements;
		this.settlements = settlements;
		this.branchSales = branchSales;
		this.scopeService = scopeService;
	}

	@Transactional(readOnly = true)
	public ReportResult<ProcurementReportRow> procurement(LocalDate from, LocalDate to, UUID supplierId) {
		validateRange(from, to);
		Specification<Procurement> specification =
				(root, query, cb) -> cb.and(root.get("status").in(POSTED_PROCUREMENT_STATUSES));
		if (from != null) {
			specification = specification.and(
					(root, query, cb) -> cb.greaterThanOrEqualTo(root.get("procurementDate"), from));
		}
		if (to != null) {
			specification = specification.and(
					(root, query, cb) -> cb.lessThanOrEqualTo(root.get("procurementDate"), to));
		}
		if (supplierId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId));
		}

		List<ProcurementReportRow> rows = new ArrayList<>();
		BigDecimal purchaseValue = BigDecimal.ZERO;
		BigDecimal commissionAmount = BigDecimal.ZERO;
		BigDecimal expenseAmount = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal paidWeight = BigDecimal.ZERO;

		for (Procurement procurement : procurements.findAll(specification)) {
			rows.add(new ProcurementReportRow(
					procurement.getReference(),
					procurement.getProcurementDate(),
					procurement.getSupplier().getName(),
					procurement.getItems().size(),
					procurement.getPaidWeightKg(),
					procurement.getPurchaseValue(),
					procurement.getCommissionAmount(),
					procurement.getExpenseAmount(),
					procurement.getTotalCost(),
					procurement.getLandedCostPerKg()));
			purchaseValue = purchaseValue.add(procurement.getPurchaseValue());
			commissionAmount = commissionAmount.add(procurement.getCommissionAmount());
			expenseAmount = expenseAmount.add(procurement.getExpenseAmount());
			totalCost = totalCost.add(procurement.getTotalCost());
			paidWeight = paidWeight.add(procurement.getPaidWeightKg());
		}

		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("purchaseValue", purchaseValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("commissionAmount", commissionAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("expenseAmount", expenseAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("totalCost", totalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("paidWeightKg", paidWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		totals.put("landedCostPerKg", divide(totalCost, paidWeight, COST_SCALE));

		return new ReportResult<>("procurement", rows, totals);
	}

	@Transactional(readOnly = true)
	public ReportResult<StockReportRow> stock(UUID chickenTypeId, LocalDate asOf) {
		LocalDate effectiveAsOf = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
		Instant endOfDay = effectiveAsOf.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		Specification<StockBatch> specification =
				(root, query, cb) -> cb.lessThan(root.get("receivedAt"), endOfDay);
		if (chickenTypeId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("chickenType").get("id"), chickenTypeId));
		}

		List<StockReportRow> rows = new ArrayList<>();
		BigDecimal receivedWeight = BigDecimal.ZERO;
		BigDecimal remainingWeight = BigDecimal.ZERO;
		BigDecimal stockValue = BigDecimal.ZERO;

		for (StockBatch batch : stockBatches.findAll(specification)) {
			LocalDate receivedDate = batch.getReceivedAt().atZone(ZoneOffset.UTC).toLocalDate();
			BigDecimal value = batch.getRemainingWeightKg()
					.multiply(batch.getLandedCostPerKg())
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			rows.add(new StockReportRow(
					batch.getId().toString(),
					batch.getChickenType().getName(),
					receivedDate,
					(int) ChronoUnit.DAYS.between(receivedDate, effectiveAsOf),
					batch.getReceivedWeightKg(),
					batch.getRemainingWeightKg(),
					batch.getLandedCostPerKg(),
					value));
			receivedWeight = receivedWeight.add(batch.getReceivedWeightKg());
			remainingWeight = remainingWeight.add(batch.getRemainingWeightKg());
			stockValue = stockValue.add(value);
		}

		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("receivedWeightKg", receivedWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		totals.put("remainingWeightKg", remainingWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		totals.put("stockValue", stockValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

		return new ReportResult<>("stock", rows, totals);
	}

	@Transactional(readOnly = true)
	public ReportResult<DistributionReportRow> distribution(LocalDate from, LocalDate to, UUID branchId) {
		validateRange(from, to);
		ReportScopeService.Scope scope = scopeService.resolve(branchId);

		Specification<Distribution> specification =
				(root, query, cb) -> cb.and(root.get("status").in(POSTED_DISTRIBUTION_STATUSES));
		if (from != null) {
			specification = specification.and(
					(root, query, cb) -> cb.greaterThanOrEqualTo(root.get("distributionDate"), from));
		}
		if (to != null) {
			specification = specification.and(
					(root, query, cb) -> cb.lessThanOrEqualTo(root.get("distributionDate"), to));
		}
		if (branchId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}

		List<DistributionReportRow> rows = new ArrayList<>();
		BigDecimal weight = BigDecimal.ZERO;
		BigDecimal cost = BigDecimal.ZERO;
		BigDecimal transferValue = BigDecimal.ZERO;
		BigDecimal ourMargin = BigDecimal.ZERO;
		BigDecimal expenses = BigDecimal.ZERO;

		for (Distribution distribution : distributions.findAll(specification)) {
			if (!scope.allows(distribution.getBranch().getId())) {
				continue;
			}
			rows.add(new DistributionReportRow(
					distribution.getReference(),
					distribution.getDistributionDate(),
					distribution.getBranch().getName(),
					distribution.getStatus().name(),
					distribution.getTotalWeightKg(),
					distribution.getTotalCost(),
					distribution.getTotalTransferValue(),
					distribution.getTotalOurMargin(),
					distribution.getExpenseAmount()));
			weight = weight.add(distribution.getTotalWeightKg());
			cost = cost.add(distribution.getTotalCost());
			transferValue = transferValue.add(distribution.getTotalTransferValue());
			ourMargin = ourMargin.add(distribution.getTotalOurMargin());
			expenses = expenses.add(distribution.getExpenseAmount());
		}

		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("weightKg", weight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));
		totals.put("cost", cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("transferValue", transferValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("ourMargin", ourMargin.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("expenseAmount", expenses.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

		return new ReportResult<>("distribution", rows, totals);
	}

	@Transactional(readOnly = true)
	public ReportResult<ProfitReportRow> profit(LocalDate from, LocalDate to, UUID branchId) {
		validateRange(from, to);
		ReportScopeService.Scope scope = scopeService.resolve(branchId);

		Specification<BranchSale> specification = (root, query, cb) -> cb.conjunction();
		if (from != null) {
			specification = specification.and(
					(root, query, cb) -> cb.greaterThanOrEqualTo(root.get("saleDate"), from));
		}
		if (to != null) {
			specification = specification.and(
					(root, query, cb) -> cb.lessThanOrEqualTo(root.get("saleDate"), to));
		}
		if (branchId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}

		Map<UUID, BranchSalesTotals> byBranch = new LinkedHashMap<>();
		for (BranchSale sale : branchSales.findAll(specification)) {
			if (!scope.allows(sale.getBranch().getId())) {
				continue;
			}
			BranchSalesTotals branchTotals = byBranch.computeIfAbsent(
					sale.getBranch().getId(),
					id -> new BranchSalesTotals(sale.getBranch().getName()));
			branchTotals.count++;
			branchTotals.totalSales = branchTotals.totalSales.add(sale.getTotalSales());
			branchTotals.transferValue = branchTotals.transferValue.add(sale.getTransferValue());
		}

		List<ProfitReportRow> rows = new ArrayList<>();
		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal totalTransferValue = BigDecimal.ZERO;
		int totalCount = 0;

		for (BranchSalesTotals branchTotals : byBranch.values()) {
			BigDecimal profit = branchTotals.totalSales.subtract(branchTotals.transferValue);
			rows.add(new ProfitReportRow(
					branchTotals.branchName,
					branchTotals.count,
					branchTotals.totalSales.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
					branchTotals.transferValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
					profit.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
					percent(profit, branchTotals.totalSales)));
			totalCount += branchTotals.count;
			totalSales = totalSales.add(branchTotals.totalSales);
			totalTransferValue = totalTransferValue.add(branchTotals.transferValue);
		}

		BigDecimal totalProfit = totalSales.subtract(totalTransferValue);
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("saleCount", BigDecimal.valueOf(totalCount));
		totals.put("totalSales", totalSales.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("transferValue", totalTransferValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("branchProfit", totalProfit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("profitPercent", percent(totalProfit, totalSales));

		return new ReportResult<>("profit", rows, totals);
	}

	@Transactional(readOnly = true)
	public ReportResult<SalesReportRow> sales(LocalDate from, LocalDate to, UUID branchId) {
		validateRange(from, to);
		ReportScopeService.Scope scope = scopeService.resolve(branchId);

		Specification<BranchSale> specification = (root, query, cb) -> cb.conjunction();
		if (from != null) {
			specification = specification.and(
					(root, query, cb) -> cb.greaterThanOrEqualTo(root.get("saleDate"), from));
		}
		if (to != null) {
			specification = specification.and(
					(root, query, cb) -> cb.lessThanOrEqualTo(root.get("saleDate"), to));
		}
		if (branchId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}

		List<SalesReportRow> rows = new ArrayList<>();
		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal totalProfit = BigDecimal.ZERO;

		for (BranchSale sale : branchSales.findAll(specification)) {
			if (!scope.allows(sale.getBranch().getId())) {
				continue;
			}
			rows.add(new SalesReportRow(
					sale.getReference(),
					sale.getSaleDate(),
					sale.getBranch().getName(),
					sale.getTotalSales(),
					sale.getTransferValue(),
					sale.getBranchProfit(),
					percent(sale.getBranchProfit(), sale.getTotalSales())));
			totalSales = totalSales.add(sale.getTotalSales());
			totalCost = totalCost.add(sale.getTransferValue());
			totalProfit = totalProfit.add(sale.getBranchProfit());
		}

		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("totalSales", totalSales.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("transferValue", totalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("branchProfit", totalProfit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		totals.put("profitPercent", percent(totalProfit, totalSales));

		return new ReportResult<>("sales", rows, totals);
	}

	@Transactional(readOnly = true)
	public ReportResult<ShrinkageReportRow> shrinkage(LocalDate from, LocalDate to, UUID branchId) {
		validateRange(from, to);
		ReportScopeService.Scope scope = scopeService.resolve(branchId);

		Instant start = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
		Instant end = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

		Specification<InventoryMovement> specification =
				(root, query, cb) -> cb.and(root.get("type").in(SHRINKAGE_MOVEMENT_TYPES));
		if (start != null) {
			specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
		}
		if (end != null) {
			specification = specification.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
		}

		List<ShrinkageReportRow> rows = new ArrayList<>();
		BigDecimal totalWeight = BigDecimal.ZERO;

		for (InventoryMovement movement : movements.findAll(specification)) {
			Distribution distribution = movement.getReferenceType() != null
					&& "DISTRIBUTION".equals(movement.getReferenceType())
							&& movement.getReferenceId() != null
							? distributions.findById(movement.getReferenceId()).orElse(null)
							: null;
			UUID movementBranchId = distribution != null ? distribution.getBranch().getId() : null;
			if (!scope.allows(movementBranchId)) {
				continue;
			}
			rows.add(new ShrinkageReportRow(
					movement.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
					movement.getStockBatch().getChickenType().getName(),
					distribution != null ? distribution.getBranch().getName() : null,
					movement.getWeightKg(),
					movement.getReason(),
					movement.getReferenceType(),
					movement.getReferenceId() != null ? movement.getReferenceId().toString() : null));
			totalWeight = totalWeight.add(movement.getWeightKg());
		}

		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("shrinkageWeightKg", totalWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP));

		return new ReportResult<>("shrinkage", rows, totals);
	}

	@Transactional(readOnly = true)
	public ReportResult<SettlementReportRow> settlements(LocalDate from, LocalDate to, UUID branchId) {
		validateRange(from, to);
		ReportScopeService.Scope scope = scopeService.resolve(branchId);

		Specification<BranchSettlement> specification =
				(root, query, cb) -> cb.and(cb.equal(root.get("status"), BranchSettlement.Status.POSTED));
		if (from != null) {
			specification = specification.and(
					(root, query, cb) -> cb.greaterThanOrEqualTo(root.get("settlementDate"), from));
		}
		if (to != null) {
			specification = specification.and(
					(root, query, cb) -> cb.lessThanOrEqualTo(root.get("settlementDate"), to));
		}
		if (branchId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}

		List<SettlementReportRow> rows = new ArrayList<>();
		BigDecimal totalAmount = BigDecimal.ZERO;

		for (BranchSettlement settlement : settlements.findAll(specification)) {
			if (!scope.allows(settlement.getBranch().getId())) {
				continue;
			}
			rows.add(new SettlementReportRow(
					settlement.getId().toString(),
					settlement.getSettlementDate(),
					settlement.getBranch().getName(),
					settlement.getMethod(),
					settlement.getReference(),
					settlement.getAmount(),
					settlement.getAllocations().size()));
			totalAmount = totalAmount.add(settlement.getAmount());
		}

		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		totals.put("settledAmount", totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

		return new ReportResult<>("settlements", rows, totals);
	}

	void validateRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException("VALIDATION_FAILED",
					"'from' must not be after 'to'", HttpStatus.BAD_REQUEST);
		}
	}

	private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale) {
		if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
		}
		return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
	}

	private static BigDecimal percent(BigDecimal amount, BigDecimal base) {
		if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
		}
		return amount.multiply(BigDecimal.valueOf(100)).divide(base, PERCENT_SCALE, RoundingMode.HALF_UP);
	}

	private static final class BranchSalesTotals {
		final String branchName;
		int count;
		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal transferValue = BigDecimal.ZERO;

		BranchSalesTotals(String branchName) {
			this.branchName = branchName;
		}
	}
}
