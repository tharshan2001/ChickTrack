package com.chick.app.sales.domain;

import com.chick.app.chicken.domain.ChickenType;
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
@Table(name = "branch_sale_items")
@Getter
@Setter
@NoArgsConstructor
public class BranchSaleItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_sale_id", nullable = false)
	private BranchSale branchSale;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chicken_type_id", nullable = false)
	private ChickenType chickenType;

	@Column(name = "weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal weightKg = BigDecimal.ZERO;

	@Column(name = "selling_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal sellingPricePerKg = BigDecimal.ZERO;

	@Column(name = "transfer_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal transferPricePerKg = BigDecimal.ZERO;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal revenue = BigDecimal.ZERO;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal cost = BigDecimal.ZERO;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal profit = BigDecimal.ZERO;
}
