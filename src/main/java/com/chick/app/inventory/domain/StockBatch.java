package com.chick.app.inventory.domain;

import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.common.domain.BaseEntity;
import com.chick.app.procurement.domain.ProcurementItem;
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
@Table(name = "stock_batches")
@Getter
@Setter
@NoArgsConstructor
public class StockBatch extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "procurement_item_id", nullable = false)
	private ProcurementItem procurementItem;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chicken_type_id", nullable = false)
	private ChickenType chickenType;

	@Column(name = "received_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal receivedWeightKg;

	@Column(name = "remaining_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal remainingWeightKg;

	@Column(name = "landed_cost_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal landedCostPerKg;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;
}
