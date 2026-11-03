package com.chick.app.pricing.domain;

import com.chick.app.branch.domain.Branch;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.common.domain.BaseEntity;
import com.chick.app.supplier.domain.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "daily_prices")
@Getter
@Setter
@NoArgsConstructor
public class DailyPrice extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chicken_type_id", nullable = false)
	private ChickenType chickenType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

	@Column(name = "purchase_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal purchasePricePerKg;

	@Column(name = "selling_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal sellingPricePerKg;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;
}
