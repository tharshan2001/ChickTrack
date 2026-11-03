package com.chick.app.distribution.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.distribution.domain.BranchReceipt;
import com.chick.app.distribution.domain.DistributionItem;
import com.chick.app.distribution.domain.DiscrepancyReason;
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

@Entity
@Table(name = "branch_receipt_items")
@Getter
@Setter
@NoArgsConstructor
public class BranchReceiptItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_receipt_id", nullable = false)
	private BranchReceipt branchReceipt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "distribution_item_id", nullable = false)
	private DistributionItem distributionItem;

	@Column(name = "received_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal receivedWeightKg;

	@Column(name = "discrepancy_weight_kg", precision = 14, scale = 3)
	private BigDecimal discrepancyWeightKg = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(name = "discrepancy_reason", length = 30)
	private DiscrepancyReason discrepancyReason;
}
