package com.chick.app.distribution.domain;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.domain.BaseEntity;
import com.chick.app.distribution.domain.Distribution;
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
@Table(name = "branch_receipts")
@Getter
@Setter
@NoArgsConstructor
public class BranchReceipt extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "distribution_id", nullable = false)
	private Distribution distribution;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(columnDefinition = "text")
	private String notes;

	@OneToMany(mappedBy = "branchReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<BranchReceiptItem> items = new ArrayList<>();
}
