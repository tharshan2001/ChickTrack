package com.chick.app.payment.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.sales.domain.BranchSale;
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
@Table(name = "branch_settlement_allocations")
@Getter
@Setter
@NoArgsConstructor
public class BranchSettlementAllocation extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_settlement_id", nullable = false)
	private BranchSettlement branchSettlement;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "distribution_id")
	private Distribution distribution;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_sale_id")
	private BranchSale branchSale;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;
}
