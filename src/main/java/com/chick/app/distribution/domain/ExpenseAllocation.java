package com.chick.app.distribution.domain;

import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "expense_allocations")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseAllocation extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "distribution_expense_id", nullable = false)
	private DistributionExpense distributionExpense;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "distribution_item_id", nullable = false)
	private DistributionItem distributionItem;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;
}
