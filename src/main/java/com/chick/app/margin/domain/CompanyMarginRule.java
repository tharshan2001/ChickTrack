package com.chick.app.margin.domain;

import com.chick.app.branch.domain.Branch;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.common.domain.BaseEntity;
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
@Table(name = "company_margin_rules")
@Getter
@Setter
@NoArgsConstructor
public class CompanyMarginRule extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chicken_type_id", nullable = false)
	private ChickenType chickenType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@Column(name = "markup_percent", nullable = false, precision = 7, scale = 4)
	private BigDecimal markupPercent = BigDecimal.ZERO;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom = Instant.now();
}
