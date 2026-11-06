package com.chick.app.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.domain.BranchSettlementAllocation;
import com.chick.app.payment.dto.BalanceResponse;
import com.chick.app.payment.dto.BranchSettlementAllocationRequest;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.OpenDeliveryResponse;
import com.chick.app.payment.mapper.SettlementMapper;
import com.chick.app.payment.repository.BranchSettlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
	private DistributionRepository distributions;

	@Mock
	private BranchReceiptRepository receipts;

	@Mock
	private com.chick.app.identity.security.BranchAccessService branchAccess;

	private SettlementService service;
	private Branch branch;
	private Distribution delivery;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new SettlementService(settlements, mapper, branches, distributions, receipts, branchAccess);

		branch = new Branch();
		branch.setId(UUID.randomUUID());
		branch.setCode("BR-001");
		branch.setName("Main Branch");

		delivery = new Distribution();
		delivery.setId(UUID.randomUUID());
		delivery.setReference("DST-2026-000001");
		delivery.setBranch(branch);
		delivery.setStatus(DistributionStatus.RECEIVED);

		when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
		when(branches.lockById(branch.getId())).thenReturn(Optional.of(branch));
		when(distributions.findById(delivery.getId())).thenReturn(Optional.of(delivery));
		when(receipts.receivedValue(delivery.getId())).thenReturn(new BigDecimal("340528.8000"));
		when(settlements.allocatedToDistribution(delivery.getId(), BranchSettlement.Status.POSTED)).thenReturn(BigDecimal.ZERO);
		when(mapper.toEntity(any(BranchSettlementCreateRequest.class))).thenReturn(new BranchSettlement());
		when(mapper.toAllocation(any(BranchSettlementAllocationRequest.class))).thenReturn(new BranchSettlementAllocation());
	}

	@Test
	void branchOwesWhatItReceivedMinusPostedSettlements() {
		when(receipts.receivedValueByDistribution(eq(branch.getId()), any())).thenReturn(List.<Object[]>of(
				new Object[] {delivery.getId(), "DST-2026-000001", Instant.now(), new BigDecimal("340528.8000")}));
		when(settlements.allocatedForBranch(branch.getId(), BranchSettlement.Status.POSTED))
				.thenReturn(new BigDecimal("150000.00"));

		BalanceResponse balance = service.branchBalance(branch.getId());

		assertThat(balance.outstandingAmount()).isEqualByComparingTo("190528.80");
	}

	@Test
	void openDeliveriesLeaveOutThoseFullyPaid() {
		UUID paidOff = UUID.randomUUID();
		when(receipts.receivedValueByDistribution(eq(branch.getId()), any())).thenReturn(List.<Object[]>of(
				new Object[] {paidOff, "DST-2026-000001", Instant.parse("2026-01-10T05:00:00Z"), new BigDecimal("1000.00")},
				new Object[] {delivery.getId(), "DST-2026-000002", Instant.parse("2026-01-12T05:00:00Z"), new BigDecimal("5000.00")}));
		when(settlements.allocatedByDistribution(branch.getId(), BranchSettlement.Status.POSTED)).thenReturn(List.<Object[]>of(
				new Object[] {paidOff, new BigDecimal("1000.00")},
				new Object[] {delivery.getId(), new BigDecimal("1500.00")}));

		List<OpenDeliveryResponse> open = service.openDeliveries(branch.getId());

		assertThat(open).singleElement().satisfies(row -> {
			assertThat(row.distributionId()).isEqualTo(delivery.getId());
			assertThat(row.owedAmount()).isEqualByComparingTo("5000.00");
			assertThat(row.outstandingAmount()).isEqualByComparingTo("3500.00");
		});
	}

	@Test
	void rejectsAllocationAboveTheOutstandingAmountOfTheDelivery() {
		assertThatThrownBy(() -> pay("350000.00", allocation(delivery, "350000.00")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("outstanding");
	}

	@Test
	void rejectsRepeatedAllocationsAgainstTheSameDeliveryThatTogetherExceedOutstanding() {
		assertThatThrownBy(() -> pay("400000.00", allocation(delivery, "200000.00"), allocation(delivery, "200000.00")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("outstanding");
	}

	@Test
	void onlyReceivedDeliveriesCanBePaid() {
		delivery.setStatus(DistributionStatus.DISPATCHED);

		assertThatThrownBy(() -> pay("100.00", allocation(delivery, "100.00")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("received");
	}

	@Test
	void allocationsMustAddUpToThePayment() {
		assertThatThrownBy(() -> pay("500.00", allocation(delivery, "400.00")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("sum to the settlement amount");
	}

	private static BranchSettlementAllocationRequest allocation(Distribution distribution, String amount) {
		return new BranchSettlementAllocationRequest(distribution.getId(), new BigDecimal(amount));
	}

	private void pay(String amount, BranchSettlementAllocationRequest... allocations) {
		service.create(new BranchSettlementCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 20),
				new BigDecimal(amount),
				"CASH",
				"REF-1",
				null,
				List.of(allocations)));
	}
}
