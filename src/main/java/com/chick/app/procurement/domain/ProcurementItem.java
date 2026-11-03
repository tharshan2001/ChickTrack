package com.chick.app.procurement.domain;

import com.chick.app.chicken.domain.ChickenType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "procurement_items")
@Getter
@Setter
@NoArgsConstructor
public class ProcurementItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "procurement_id", nullable = false)
	private Procurement procurement;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chicken_type_id", nullable = false)
	private ChickenType chickenType;

	@Column(name = "paid_weight_kg", nullable = false, precision = 14, scale = 3)
	private BigDecimal paidWeightKg;

	@Column(name = "purchase_price_per_kg", nullable = false, precision = 14, scale = 4)
	private BigDecimal purchasePricePerKg;

	@Enumerated(EnumType.STRING)
	@Column(name = "commission_method", nullable = false, length = 20)
	private CommissionMethod commissionMethod = CommissionMethod.FIXED;

	@Column(name = "commission_value", precision = 14, scale = 4)
	private BigDecimal commissionValue = BigDecimal.ZERO;

	@Column(name = "quantity")
	private Integer quantity;

	@Column(name = "purchase_value", precision = 16, scale = 2)
	private BigDecimal purchaseValue = BigDecimal.ZERO;

	@Column(name = "commission_amount", precision = 16, scale = 2)
	private BigDecimal commissionAmount = BigDecimal.ZERO;

	@Column(name = "allocated_expense_amount", precision = 16, scale = 2)
	private BigDecimal allocatedExpenseAmount = BigDecimal.ZERO;

	@Column(name = "total_cost", precision = 16, scale = 2)
	private BigDecimal totalCost = BigDecimal.ZERO;

	@Column(name = "received_weight_kg", precision = 14, scale = 3)
	private BigDecimal receivedWeightKg = BigDecimal.ZERO;

	@Column(name = "landed_cost_per_kg", precision = 14, scale = 4)
	private BigDecimal landedCostPerKg;
}
