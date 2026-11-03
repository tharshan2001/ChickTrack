package com.chick.app.procurement.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.expense.domain.ExpenseCategory;
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
@Table(name = "procurement_expenses")
@Getter
@Setter
@NoArgsConstructor
public class ProcurementExpense extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "procurement_id", nullable = false)
	private Procurement procurement;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private ExpenseCategory category;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;
}
