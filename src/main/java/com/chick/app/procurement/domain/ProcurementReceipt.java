package com.chick.app.procurement.domain;

import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "procurement_receipts")
@Getter
@Setter
@NoArgsConstructor
public class ProcurementReceipt extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "procurement_id", nullable = false)
	private Procurement procurement;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(columnDefinition = "text")
	private String notes;

	@OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProcurementReceiptItem> items = new ArrayList<>();
}
