package com.chick.app.report.service;

import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.time.BusinessTime;
import com.chick.app.distribution.domain.BranchReceiptItem;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionItem;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.expense.domain.OperatingExpense;
import com.chick.app.expense.repository.OperatingExpenseRepository;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.report.dto.ProfitLossResponse;
import com.chick.app.report.dto.ProfitLossResponse.Branches;
import com.chick.app.report.dto.ProfitLossResponse.CategoryAmount;
import com.chick.app.report.dto.ProfitLossResponse.HeadOffice;
import com.chick.app.report.dto.ProfitLossResponse.OperatingExpenses;
import com.chick.app.report.dto.ProfitLossResponse.PeriodStatement;
import com.chick.app.report.dto.ProfitLossResponse.Statement;
import com.chick.app.report.dto.ProfitLossResponse.StockLosses;
import com.chick.app.report.export.ReportTable;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profit and loss for a day, a month, a year or any period, optionally broken down by day, month
 * or year.
 *
 * <pre>
 * head office margin   = transfer value - landed cost - distribution expenses   (posted distributions, by distribution date)
 * branch profit        = branch sales - transfer value of the stock sold        (branch sales, by sale date)
 * stock losses         = warehouse loss/damage at landed cost                   (by the day recorded)
 *                      + receiving discrepancies at the transfer price          (by the receipt's receivedAt)
 * operating expenses   = posted operating expenses                              (by expense date)
 * net profit           = head office margin + branch profit - stock losses - operating expenses
 * </pre>
 *
 * Head office margin is recognised when stock is dispatched, so stock a branch still holds at the
 * end of the period already carries our margin. Procurement commission and expenses are inside the
 * landed cost, so they are not subtracted again.
 */
@Service
public class ProfitLossService {

	public enum Period { DAILY, MONTHLY, YEARLY, CUSTOM }

	public enum GroupBy { NONE, DAY, MONTH, YEAR }

	private static final int MONEY_SCALE = 2;
	private static final int WEIGHT_SCALE = 3;
	private static final int MAX_BUCKETS = 400;
	private static final long MAX_DAYS = 3660;
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final DistributionRepository distributions;
	private final BranchReceiptRepository receipts;
	private final BranchSaleRepository branchSales;
	private final InventoryMovementRepository movements;
	private final OperatingExpenseRepository operatingExpenses;
	private final ReportScopeService scopeService;

	public ProfitLossService(
			DistributionRepository distributions,
			BranchReceiptRepository receipts,
			BranchSaleRepository branchSales,
			InventoryMovementRepository movements,
			OperatingExpenseRepository operatingExpenses,
			ReportScopeService scopeService) {
		this.distributions = distributions;
		this.receipts = receipts;
		this.branchSales = branchSales;
		this.movements = movements;
		this.operatingExpenses = operatingExpenses;
		this.scopeService = scopeService;
	}

	/** The request after defaults: the period, its dates and the grouping. */
	public record Range(Period period, LocalDate from, LocalDate to, GroupBy groupBy) {
	}

	/**
	 * Resolves the period. {@code date} picks the day, month or year (default today); {@code month}
	 * ({@code 2026-11}) and {@code year} pick a month or year directly; CUSTOM needs {@code from}
	 * and {@code to}. Grouping defaults to none for a day or custom period, days for a month and
	 * months for a year.
	 */
	public Range resolve(String period, LocalDate date, String month, Integer year, LocalDate from, LocalDate to,
			String groupBy) {
		Period p = parse(Period.class, period, month != null ? Period.MONTHLY : year != null ? Period.YEARLY
				: from != null || to != null ? Period.CUSTOM : Period.MONTHLY, "period");
		LocalDate anchor = date != null ? date : BusinessTime.today();
		LocalDate start;
		LocalDate end;
		GroupBy defaultGroup;
		switch (p) {
			case DAILY -> {
				start = anchor;
				end = anchor;
				defaultGroup = GroupBy.NONE;
			}
			case MONTHLY -> {
				YearMonth ym = month != null ? parseMonth(month) : YearMonth.from(anchor);
				start = ym.atDay(1);
				end = ym.atEndOfMonth();
				defaultGroup = GroupBy.DAY;
			}
			case YEARLY -> {
				int y = year != null ? year : anchor.getYear();
				if (y < 2000 || y > 9999) {
					throw invalid("year must be between 2000 and 9999");
				}
				start = LocalDate.of(y, 1, 1);
				end = LocalDate.of(y, 12, 31);
				defaultGroup = GroupBy.MONTH;
			}
			default -> {
				if (from == null || to == null) {
					throw invalid("A custom period needs both 'from' and 'to'");
				}
				if (from.isAfter(to)) {
					throw invalid("'from' must not be after 'to'");
				}
				if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
					throw invalid("A custom period can cover at most 10 years");
				}
				start = from;
				end = to;
				defaultGroup = GroupBy.NONE;
			}
		}
		GroupBy g = parse(GroupBy.class, groupBy, defaultGroup, "groupBy");
		Range range = new Range(p, start, end, g);
		if (buckets(range).size() > MAX_BUCKETS) {
			throw invalid("Too many periods to break down (" + MAX_BUCKETS + " at most); use a coarser groupBy");
		}
		return range;
	}

	@Transactional(readOnly = true)
	public ProfitLossResponse profitLoss(Range range, UUID branchId) {
		ReportScopeService.Scope scope = scopeService.resolve(branchId);
		LocalDate from = range.from();
		LocalDate to = range.to();

		Totals summary = new Totals();
		TreeMap<LocalDate, Bucket> buckets = new TreeMap<>();
		for (Bucket bucket : buckets(range)) {
			buckets.put(bucket.from, bucket);
		}

		// Head office: posted distributions by distribution date.
		Specification<Distribution> distributionSpec = (root, query, cb) -> cb.and(
				root.get("status").in(ReportService.POSTED_DISTRIBUTION_STATUSES),
				cb.greaterThanOrEqualTo(root.get("distributionDate"), from),
				cb.lessThanOrEqualTo(root.get("distributionDate"), to));
		if (branchId != null) {
			distributionSpec = distributionSpec.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		for (Distribution distribution : distributions.findAll(distributionSpec)) {
			if (!scope.allows(distribution.getBranch().getId())) {
				continue;
			}
			for (Totals totals : targets(summary, buckets, distribution.getDistributionDate())) {
				totals.transferRevenue = totals.transferRevenue.add(distribution.getTotalTransferValue());
				totals.costOfGoods = totals.costOfGoods.add(distribution.getTotalCost());
				totals.distributionExpenses = totals.distributionExpenses.add(distribution.getExpenseAmount());
				totals.headOfficeMargin = totals.headOfficeMargin.add(distribution.getTotalOurMargin());
			}
		}

		// Branches: sales by sale date.
		Specification<BranchSale> salesSpec = (root, query, cb) -> cb.and(
				cb.greaterThanOrEqualTo(root.get("saleDate"), from),
				cb.lessThanOrEqualTo(root.get("saleDate"), to));
		if (branchId != null) {
			salesSpec = salesSpec.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		for (BranchSale sale : branchSales.findAll(salesSpec)) {
			if (!scope.allows(sale.getBranch().getId())) {
				continue;
			}
			for (Totals totals : targets(summary, buckets, sale.getSaleDate())) {
				totals.branchSales = totals.branchSales.add(sale.getTotalSales());
				totals.branchCostOfSales = totals.branchCostOfSales.add(sale.getTransferValue());
			}
		}

		// Warehouse losses: loss and damage adjustments by the day they were recorded. They belong to
		// no branch, so only all-branch users without a branch filter see them.
		if (branchId == null && scope.allows(null)) {
			Specification<InventoryMovement> lossSpec = (root, query, cb) -> cb.and(
					root.get("type").in(ReportService.SHRINKAGE_MOVEMENT_TYPES),
					cb.or(cb.isNull(root.get("referenceType")), cb.notEqual(root.get("referenceType"), "DISTRIBUTION")),
					cb.greaterThanOrEqualTo(root.get("createdAt"), BusinessTime.startOf(from)),
					cb.lessThan(root.get("createdAt"), BusinessTime.startOf(to.plusDays(1))));
			for (InventoryMovement movement : movements.findAll(lossSpec)) {
				BigDecimal weight = movement.getWeightKg();
				BigDecimal value = weight.multiply(movement.getStockBatch().getLandedCostPerKg());
				for (Totals totals : targets(summary, buckets, BusinessTime.dateOf(movement.getCreatedAt()))) {
					totals.stockLossWeightKg = totals.stockLossWeightKg.add(weight);
					totals.warehouseLossValue = totals.warehouseLossValue.add(value);
				}
			}
		}

		// Transit losses: receiving discrepancies dated by when the branch received the stock, so a
		// receipt entered late still lands in the right period.
		for (BranchReceiptItem item : receipts.discrepancies(BusinessTime.startOf(from), BusinessTime.startOf(to.plusDays(1)))) {
			Distribution distribution = item.getBranchReceipt().getDistribution();
			UUID receiptBranchId = distribution.getBranch().getId();
			if (branchId != null ? !branchId.equals(receiptBranchId) : !scope.allows(receiptBranchId)) {
				continue;
			}
			BigDecimal weight = item.getDiscrepancyWeightKg();
			BigDecimal value = weight.multiply(transitPricePerKg(distribution, item.getDistributionItem()));
			for (Totals totals : targets(summary, buckets, BusinessTime.dateOf(item.getBranchReceipt().getReceivedAt()))) {
				totals.stockLossWeightKg = totals.stockLossWeightKg.add(weight);
				totals.transitLossValue = totals.transitLossValue.add(value);
			}
		}

		// Operating expenses by expense date. Head-office expenses (no branch) only without a branch filter.
		Specification<OperatingExpense> expenseSpec = (root, query, cb) -> cb.and(
				cb.equal(root.get("status"), OperatingExpense.Status.POSTED),
				cb.greaterThanOrEqualTo(root.get("expenseDate"), from),
				cb.lessThanOrEqualTo(root.get("expenseDate"), to));
		if (branchId != null) {
			expenseSpec = expenseSpec.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		for (OperatingExpense expense : operatingExpenses.findAll(expenseSpec)) {
			UUID expenseBranchId = expense.getBranch() == null ? null : expense.getBranch().getId();
			if (!scope.allows(expenseBranchId)) {
				continue;
			}
			for (Totals totals : targets(summary, buckets, expense.getExpenseDate())) {
				totals.addExpense(expense.getCategory().getId(), expense.getCategory().getName(), expense.getAmount());
			}
		}

		List<PeriodStatement> breakdown = new ArrayList<>();
		if (range.groupBy() != GroupBy.NONE) {
			for (Bucket bucket : buckets.values()) {
				breakdown.add(new PeriodStatement(bucket.label, bucket.from, bucket.to, bucket.totals.toStatement()));
			}
		}
		return new ProfitLossResponse(
				range.period().name(),
				from,
				to,
				range.groupBy().name(),
				branchId,
				summary.toStatement(),
				breakdown);
	}

	/** The same figures as a table for CSV / PDF export: one row per period (or one for the summary). */
	public ReportTable toTable(ProfitLossResponse response) {
		List<String> headers = List.of("Period", "From", "To", "Transfer Revenue", "Cost Of Goods",
				"Distribution Expenses", "Head Office Margin", "Branch Sales", "Branch Cost Of Sales",
				"Branch Profit", "Stock Loss Kg", "Stock Loss Value", "Operating Expenses", "Net Profit",
				"Net Margin Percent");
		List<List<String>> rows = new ArrayList<>();
		if (response.breakdown().isEmpty()) {
			rows.add(row(response.from() + (response.from().equals(response.to()) ? "" : " to " + response.to()),
					response.from(), response.to(), response.summary()));
		}
		else {
			for (PeriodStatement period : response.breakdown()) {
				rows.add(row(period.label(), period.from(), period.to(), period.statement()));
			}
		}
		Statement s = response.summary();
		Map<String, String> totals = new LinkedHashMap<>();
		totals.put("Transfer Revenue", s.headOffice().transferRevenue().toPlainString());
		totals.put("Head Office Margin", s.headOffice().margin().toPlainString());
		totals.put("Branch Sales", s.branches().sales().toPlainString());
		totals.put("Branch Profit", s.branches().profit().toPlainString());
		totals.put("Stock Loss Value", s.stockLosses().value().toPlainString());
		for (CategoryAmount category : s.operatingExpenses().byCategory()) {
			totals.put("Expense: " + category.categoryName(), category.amount().toPlainString());
		}
		totals.put("Operating Expenses", s.operatingExpenses().total().toPlainString());
		totals.put("Net Profit", s.netProfit().toPlainString());
		totals.put("Net Margin Percent", s.netMarginPercent() == null ? "" : s.netMarginPercent().toPlainString());
		return new ReportTable("profit-loss", headers, rows, totals);
	}

	private static List<String> row(String label, LocalDate from, LocalDate to, Statement s) {
		return List.of(
				label,
				from.toString(),
				to.toString(),
				s.headOffice().transferRevenue().toPlainString(),
				s.headOffice().costOfGoods().toPlainString(),
				s.headOffice().distributionExpenses().toPlainString(),
				s.headOffice().margin().toPlainString(),
				s.branches().sales().toPlainString(),
				s.branches().costOfSales().toPlainString(),
				s.branches().profit().toPlainString(),
				s.stockLosses().weightKg().toPlainString(),
				s.stockLosses().value().toPlainString(),
				s.operatingExpenses().total().toPlainString(),
				s.netProfit().toPlainString(),
				s.netMarginPercent() == null ? "" : s.netMarginPercent().toPlainString());
	}

	/**
	 * A receiving discrepancy is valued at the line's transfer price: the branch is never charged for
	 * weight it did not receive, though our margin was counted at dispatch. A reversed distribution
	 * counted no margin, so its losses stay at landed cost.
	 */
	private static BigDecimal transitPricePerKg(Distribution distribution, DistributionItem line) {
		BigDecimal transferPrice = line.getTransferPricePerKg();
		if (ReportService.POSTED_DISTRIBUTION_STATUSES.contains(distribution.getStatus())
				&& transferPrice != null && transferPrice.signum() > 0) {
			return transferPrice;
		}
		return line.getStockBatch().getLandedCostPerKg();
	}

	private static List<Totals> targets(Totals summary, TreeMap<LocalDate, Bucket> buckets, LocalDate date) {
		Map.Entry<LocalDate, Bucket> entry = buckets.floorEntry(date);
		if (entry == null || date.isAfter(entry.getValue().to)) {
			return List.of(summary);
		}
		return List.of(summary, entry.getValue().totals);
	}

	private static List<Bucket> buckets(Range range) {
		List<Bucket> buckets = new ArrayList<>();
		if (range.groupBy() == GroupBy.NONE) {
			return buckets;
		}
		LocalDate cursor = range.from();
		while (!cursor.isAfter(range.to())) {
			LocalDate end;
			String label;
			switch (range.groupBy()) {
				case DAY -> {
					end = cursor;
					label = cursor.toString();
				}
				case MONTH -> {
					end = YearMonth.from(cursor).atEndOfMonth();
					label = YearMonth.from(cursor).toString();
				}
				default -> {
					end = LocalDate.of(cursor.getYear(), 12, 31);
					label = String.valueOf(cursor.getYear());
				}
			}
			if (end.isAfter(range.to())) {
				end = range.to();
			}
			buckets.add(new Bucket(label, cursor, end));
			if (buckets.size() > MAX_BUCKETS) {
				break;
			}
			cursor = end.plusDays(1);
		}
		return buckets;
	}

	private static YearMonth parseMonth(String month) {
		try {
			return YearMonth.parse(month.trim());
		}
		catch (DateTimeParseException ex) {
			throw invalid("month must look like 2026-11");
		}
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback, String name) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			StringBuilder allowed = new StringBuilder();
			for (E constant : type.getEnumConstants()) {
				allowed.append(allowed.length() == 0 ? "" : ", ").append(constant.name().toLowerCase(Locale.ROOT));
			}
			throw invalid(name + " must be one of " + allowed);
		}
	}

	private static BusinessException invalid(String message) {
		return new BusinessException("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
	}

	private static final class Bucket {
		final String label;
		final LocalDate from;
		final LocalDate to;
		final Totals totals = new Totals();

		Bucket(String label, LocalDate from, LocalDate to) {
			this.label = label;
			this.from = from;
			this.to = to;
		}
	}

	private static final class Totals {
		BigDecimal transferRevenue = BigDecimal.ZERO;
		BigDecimal costOfGoods = BigDecimal.ZERO;
		BigDecimal distributionExpenses = BigDecimal.ZERO;
		BigDecimal headOfficeMargin = BigDecimal.ZERO;
		BigDecimal branchSales = BigDecimal.ZERO;
		BigDecimal branchCostOfSales = BigDecimal.ZERO;
		BigDecimal stockLossWeightKg = BigDecimal.ZERO;
		BigDecimal warehouseLossValue = BigDecimal.ZERO;
		BigDecimal transitLossValue = BigDecimal.ZERO;
		final Map<UUID, CategoryAmount> expenses = new LinkedHashMap<>();

		void addExpense(UUID categoryId, String categoryName, BigDecimal amount) {
			expenses.merge(categoryId, new CategoryAmount(categoryId, categoryName, amount),
					(a, b) -> new CategoryAmount(categoryId, categoryName, a.amount().add(b.amount())));
		}

		Statement toStatement() {
			BigDecimal margin = money(headOfficeMargin);
			BigDecimal sales = money(branchSales);
			BigDecimal costOfSales = money(branchCostOfSales);
			BigDecimal branchProfit = sales.subtract(costOfSales);
			BigDecimal warehouseLoss = money(warehouseLossValue);
			BigDecimal transitLoss = money(transitLossValue);
			BigDecimal lossValue = warehouseLoss.add(transitLoss);
			List<CategoryAmount> byCategory = expenses.values().stream()
					.map(c -> new CategoryAmount(c.categoryId(), c.categoryName(), money(c.amount())))
					.sorted(Comparator.comparing(CategoryAmount::amount).reversed()
							.thenComparing(CategoryAmount::categoryName))
					.toList();
			BigDecimal expenseTotal = byCategory.stream().map(CategoryAmount::amount)
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			BigDecimal net = margin.add(branchProfit).subtract(lossValue).subtract(expenseTotal);
			BigDecimal netPercent = sales.signum() > 0
					? net.multiply(HUNDRED).divide(sales, MONEY_SCALE, RoundingMode.HALF_UP)
					: null;
			return new Statement(
					new HeadOffice(money(transferRevenue), money(costOfGoods), money(distributionExpenses), margin),
					new Branches(sales, costOfSales, branchProfit),
					new StockLosses(stockLossWeightKg.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP),
							warehouseLoss, transitLoss, lossValue),
					new OperatingExpenses(expenseTotal, byCategory),
					net,
					netPercent);
		}

		private static BigDecimal money(BigDecimal value) {
			return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		}
	}
}
