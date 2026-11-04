package com.chick.app.distribution.domain;

import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.common.domain.BaseEntity;
import com.chick.app.inventory.domain.StockBatch;
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
@Table(name = "distribution_items")
@Getter
@Setter
@NoArgsConstructor
public class DistributionItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "distribution_id", nullable = false)
	private Distribution distribution;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stock_batch_id", nullable = false)
	private StockBatch stockBatch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chicken_type_id", nullable = false)
	private ChickenType chickenType;

	@Column(name = "weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal weightKg;

	@Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
	private BigDecimal unitCost;

	@Column(name = "our_margin_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal ourMarginPerKg = BigDecimal.ZERO;

	@Column(name = "distribution_expense_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal distributionExpensePerKg = BigDecimal.ZERO;

	@Column(name = "transfer_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal transferPricePerKg = BigDecimal.ZERO;

	@Column(name = "line_cost", nullable = false, precision = 16, scale = 2)
	private BigDecimal lineCost;

	@Column(name = "line_transfer_value", nullable = false, precision = 16, scale = 2)
	private BigDecimal lineTransferValue = BigDecimal.ZERO;
}
