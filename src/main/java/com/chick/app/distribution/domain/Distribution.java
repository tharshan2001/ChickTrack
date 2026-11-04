package com.chick.app.distribution.domain;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "distributions")
@Getter
@Setter
@NoArgsConstructor
public class Distribution extends BaseEntity {

	@Column(nullable = false, unique = true, length = 40)
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(name = "distribution_date", nullable = false)
	private LocalDate distributionDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DistributionStatus status = DistributionStatus.DRAFT;

	@Column(name = "total_weight_kg", precision = 14, scale = 3)
	private BigDecimal totalWeightKg = BigDecimal.ZERO;

	@Column(name = "total_cost", precision = 16, scale = 2)
	private BigDecimal totalCost = BigDecimal.ZERO;

	@Column(name = "total_transfer_value", precision = 16, scale = 2)
	private BigDecimal totalTransferValue = BigDecimal.ZERO;

	@Column(name = "total_our_margin", precision = 16, scale = 2)
	private BigDecimal totalOurMargin = BigDecimal.ZERO;

	@Column(name = "expense_amount", precision = 16, scale = 2)
	private BigDecimal expenseAmount = BigDecimal.ZERO;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "reversal_reason", columnDefinition = "text")
	private String reversalReason;

	@OneToMany(mappedBy = "distribution", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DistributionItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "distribution", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DistributionExpense> expenses = new ArrayList<>();

	@OneToMany(mappedBy = "distribution", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<BranchReceipt> receipts = new ArrayList<>();
}
