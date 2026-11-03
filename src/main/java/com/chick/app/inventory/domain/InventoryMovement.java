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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
public class InventoryMovement extends BaseEntity {

	public enum MovementType {
		RECEIPT,
		DISTRIBUTION,
		RETURN,
		DAMAGE,
		LOSS,
		ADJUSTMENT,
		REVERSAL
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "stock_batch_id", nullable = false)
	private StockBatch stockBatch;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MovementType type;

	@Column(name = "weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal weightKg;

	@Column(name = "reference_type", length = 50)
	private String referenceType;

	@Column(name = "reference_id")
	private UUID referenceId;

	@Column(columnDefinition = "text")
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();
}
