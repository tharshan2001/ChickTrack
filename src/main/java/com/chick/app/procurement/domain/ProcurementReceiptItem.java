package com.chick.app.procurement.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.procurement.domain.ProcurementItem;
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
@Table(name = "procurement_receipt_items")
@Getter
@Setter
@NoArgsConstructor
public class ProcurementReceiptItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receipt_id", nullable = false)
	private ProcurementReceipt receipt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "procurement_item_id", nullable = false)
	private ProcurementItem procurementItem;

	@Column(name = "received_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal receivedWeightKg;
}
