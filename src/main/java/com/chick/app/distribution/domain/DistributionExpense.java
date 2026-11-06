package com.chick.app.distribution.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.expense.domain.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "distribution_expenses")
@Getter
@Setter
@NoArgsConstructor
public class DistributionExpense extends BaseEntity {

	public enum AllocationMethod {
		BY_WEIGHT,
		FIXED,
		MANUAL
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "distribution_id", nullable = false)
	private Distribution distribution;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private ExpenseCategory category;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "allocation_method", nullable = false, length = 20)
	private AllocationMethod allocationMethod = AllocationMethod.BY_WEIGHT;

	/** MANUAL only, while the distribution is created: amount per chicken type (kept as expense_allocations). */
	@jakarta.persistence.Transient
	private java.util.Map<java.util.UUID, BigDecimal> manualShareByType = new java.util.LinkedHashMap<>();
}
