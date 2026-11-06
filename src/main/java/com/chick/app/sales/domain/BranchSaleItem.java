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

	/** Stock the branch held of this type before the day closed. */
	@Column(name = "opening_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal openingWeightKg = BigDecimal.ZERO;

	/** Stock the branch counted at the end of the day. */
	@Column(name = "closing_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal closingWeightKg = BigDecimal.ZERO;

	/** Weight that left the branch that day: opening - closing (sold, plus any waste). */
	@Column(name = "weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal weightKg = BigDecimal.ZERO;

	/** Branch selling price that day, used for expected sales; null when none is set. */
	@Column(name = "selling_price_per_kg", precision = 14, scale = 4)
	private BigDecimal sellingPricePerKg;

	@Column(name = "transfer_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal transferPricePerKg = BigDecimal.ZERO;

	/** Expected revenue of this line at the branch price (sales are entered per day, not per type). */
	@Column(precision = 16, scale = 2)
	private BigDecimal revenue;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal cost = BigDecimal.ZERO;

	/** Expected profit of this line at the branch price; null when no price is set. */
	@Column(precision = 16, scale = 2)
	private BigDecimal profit;
}
