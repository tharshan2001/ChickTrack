package com.chick.app.procurement.domain;

import com.chick.app.common.domain.BaseEntity;
import com.chick.app.supplier.domain.Supplier;
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
@Table(name = "procurements")
@Getter
@Setter
@NoArgsConstructor
public class Procurement extends BaseEntity {

	@Column(nullable = false, unique = true, length = 40)
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Column(name = "procurement_date", nullable = false)
	private LocalDate procurementDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ProcurementStatus status = ProcurementStatus.DRAFT;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "purchase_value", precision = 16, scale = 2)
	private BigDecimal purchaseValue = BigDecimal.ZERO;

	@Column(name = "commission_amount", precision = 16, scale = 2)
	private BigDecimal commissionAmount = BigDecimal.ZERO;

	@Column(name = "expense_amount", precision = 16, scale = 2)
	private BigDecimal expenseAmount = BigDecimal.ZERO;

	@Column(name = "total_cost", precision = 16, scale = 2)
	private BigDecimal totalCost = BigDecimal.ZERO;

	@Column(name = "paid_weight_kg", precision = 14, scale = 3)
	private BigDecimal paidWeightKg = BigDecimal.ZERO;

	@Column(name = "received_weight_kg", precision = 14, scale = 3)
	private BigDecimal receivedWeightKg = BigDecimal.ZERO;

	@Column(name = "landed_cost_per_kg", precision = 14, scale = 4)
	private BigDecimal landedCostPerKg;

	@Column(name = "reversal_reason", columnDefinition = "text")
	private String reversalReason;

	@OneToMany(mappedBy = "procurement", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProcurementItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "procurement", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProcurementExpense> expenses = new ArrayList<>();

	@OneToMany(mappedBy = "procurement", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProcurementReceipt> receipts = new ArrayList<>();
}
