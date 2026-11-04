package com.chick.app.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.domain.BranchSettlementAllocation;
import com.chick.app.payment.dto.BranchSettlementAllocationRequest;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BalanceResponse;
import com.chick.app.payment.mapper.SettlementMapper;
import com.chick.app.payment.repository.BranchSettlementRepository;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SettlementServiceTest {

	@Mock
	private BranchSettlementRepository settlements;

	@Mock
	private SettlementMapper mapper;

	@Mock
	private BranchRepository branches;

	@Mock
	private BranchSaleRepository branchSales;

	@Mock
	private com.chick.app.identity.security.BranchAccessService branchAccess;

	private SettlementService service;
	private Branch branch;
	private BranchSale sale;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new SettlementService(settlements, mapper, branches, branchSales, branchAccess);

		branch = new Branch();
		branch.setId(UUID.randomUUID());
		branch.setCode("BR-001");
		branch.setName("Main Branch");

		sale = new BranchSale();
		sale.setId(UUID.randomUUID());
		sale.setBranch(branch);
		sale.setSaleDate(LocalDate.of(2026, 1, 15));
		sale.setTotalSales(new BigDecimal("410000.00"));
		sale.setTransferValue(new BigDecimal("340528.80"));
		sale.setBranchProfit(new BigDecimal("69471.20"));

		when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
		when(branches.lockById(branch.getId())).thenReturn(Optional.of(branch));
		when(branchSales.findById(sale.getId())).thenReturn(Optional.of(sale));
		when(mapper.toEntity(any(BranchSettlementCreateRequest.class))).thenReturn(new BranchSettlement());
		when(mapper.toAllocation(any(BranchSettlementAllocationRequest.class))).thenReturn(new BranchSettlementAllocation());
	}

	@Test
	void branchBalanceIsEnteredSalesMinusPostedSettlements() {
		when(branchSales.totalTransferValue(branch.getId())).thenReturn(new BigDecimal("340528.80"));
		when(settlements.allocatedForBranch(branch.getId(), BranchSettlement.Status.POSTED))
				.thenReturn(new BigDecimal("150000.00"));

		BalanceResponse balance = service.branchBalance(branch.getId());

		assertThat(balance.outstandingAmount()).isEqualByComparingTo("190528.80");
	}

	@Test
	void rejectsAllocationAboveTheOutstandingAmountOfTheSale() {
		when(settlements.allocatedToSale(sale.getId(), BranchSettlement.Status.POSTED)).thenReturn(BigDecimal.ZERO);

		assertThatThrownBy(() -> service.create(new BranchSettlementCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 20),
				new BigDecimal("350000.00"),
				"CASH",
				"REF-1",
				null,
				List.of(new BranchSettlementAllocationRequest(sale.getId(), new BigDecimal("350000.00"))))))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("outstanding");
	}

	@Test
	void rejectsRepeatedAllocationsAgainstTheSameSaleThatTogetherExceedOutstanding() {
		when(settlements.allocatedToSale(sale.getId(), BranchSettlement.Status.POSTED)).thenReturn(BigDecimal.ZERO);

		assertThatThrownBy(() -> service.create(new BranchSettlementCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 20),
				new BigDecimal("400000.00"),
				"CASH",
				"REF-2",
				null,
				List.of(
						new BranchSettlementAllocationRequest(sale.getId(), new BigDecimal("200000.00")),
						new BranchSettlementAllocationRequest(sale.getId(), new BigDecimal("200000.00"))))))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("outstanding");
	}
}
