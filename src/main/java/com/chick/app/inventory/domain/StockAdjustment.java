package com.chick.app.inventory.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A warehouse correction to a batch. Negative weight removes stock, positive weight adds it back. */
@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class StockAdjustment extends BaseEntity {

	public enum Type { DAMAGE, LOSS, RETURN, ADJUSTMENT }

	@Column(nullable = false, length = 40)
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stock_batch_id", nullable = false)
	private StockBatch stockBatch;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Type type;

	@Column(name = "weight_change_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal weightChangeKg;

	@Column(nullable = false, columnDefinition = "text")
	private String reason;
}
