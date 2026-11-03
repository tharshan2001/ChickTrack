package com.chick.app.sales.domain;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branch_sales")
@Getter
@Setter
@NoArgsConstructor
public class BranchSale extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(nullable = false, unique = true, length = 40)
	private String reference;

	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@Column(name = "total_sales", nullable = false, precision = 16, scale = 2)
	private BigDecimal totalSales = BigDecimal.ZERO;

	@Column(name = "cost_amount", nullable = false, precision = 16, scale = 2)
	private BigDecimal costAmount = BigDecimal.ZERO;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal profit = BigDecimal.ZERO;

	@Column(columnDefinition = "text")
	private String notes;
}
