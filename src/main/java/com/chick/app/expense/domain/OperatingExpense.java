package com.chick.app.expense.domain;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A running cost (rent, salaries, power) charged to head office ({@code branch} null) or to a branch. */
@Entity
@Table(name = "operating_expenses")
@Getter
@Setter
@NoArgsConstructor
public class OperatingExpense extends BaseEntity {

	public enum Status { POSTED, VOIDED }

	@Column(nullable = false, unique = true, length = 40)
	private String reference;

	@Column(name = "expense_date", nullable = false)
	private LocalDate expenseDate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_category_id", nullable = false)
	private ExpenseCategory category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(length = 150)
	private String payee;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.POSTED;

	@Column(name = "void_reason", columnDefinition = "text")
	private String voidReason;
}
