package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Profit and loss for a period: {@code summary} covers the whole period, {@code breakdown} one
 * statement per day, month or year inside it (empty when {@code groupBy} is NONE).
 */
public record ProfitLossResponse(
		String period,
		LocalDate from,
		LocalDate to,
		String groupBy,
		UUID branchId,
		Statement summary,
		List<PeriodStatement> breakdown) {

	public record PeriodStatement(String label, LocalDate from, LocalDate to, Statement statement) {
	}

	/**
	 * netProfit = headOffice.margin + branches.profit - stockLosses.value - operatingExpenses.total.
	 * netMarginPercent is netProfit over branch sales (null without sales).
	 */
	public record Statement(
			HeadOffice headOffice,
			Branches branches,
			StockLosses stockLosses,
			OperatingExpenses operatingExpenses,
			BigDecimal netProfit,
			BigDecimal netMarginPercent) {
	}

	/** Stock sent to branches: what branches are charged, our landed cost, passed-through expenses, our margin. */
	public record HeadOffice(
			BigDecimal transferRevenue,
			BigDecimal costOfGoods,
			BigDecimal distributionExpenses,
			BigDecimal margin) {
	}

	/** What branches sold to customers, what that stock cost them (transfer value), and their profit. */
	public record Branches(BigDecimal sales, BigDecimal costOfSales, BigDecimal profit) {
	}

	/** Loss and damage: warehouse losses at landed cost, in-transit losses at the transfer price. */
	public record StockLosses(
			BigDecimal weightKg,
			BigDecimal warehouseValue,
			BigDecimal transitValue,
			BigDecimal value) {
	}

	public record OperatingExpenses(BigDecimal total, List<CategoryAmount> byCategory) {
	}

	public record CategoryAmount(UUID categoryId, String categoryName, BigDecimal amount) {
	}
}
