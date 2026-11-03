package com.chick.app.payment.domain;

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
@Table(name = "branch_settlements")
@Getter
@Setter
@NoArgsConstructor
public class BranchSettlement extends BaseEntity {

	public enum Status { POSTED, REVERSED }

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(name = "settlement_date", nullable = false)
	private LocalDate settlementDate;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 40)
	private String method;

	@Column(length = 100)
	private String reference;

	@Column(columnDefinition = "text")
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status = Status.POSTED;

	@Column(name = "reversal_reason", columnDefinition = "text")
	private String reversalReason;

	@OneToMany(mappedBy = "branchSettlement", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<BranchSettlementAllocation> allocations = new ArrayList<>();
}
