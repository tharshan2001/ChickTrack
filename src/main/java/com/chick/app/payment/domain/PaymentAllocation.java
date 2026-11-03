package com.chick.app.payment.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.procurement.domain.Procurement;
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
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAllocation extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "procurement_id", nullable = false)
	private Procurement procurement;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal amount;
}
