package com.chick.app.distribution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.domain.BranchReceipt;
import com.chick.app.distribution.domain.BranchReceiptItem;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionItem;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.domain.DiscrepancyReason;
import com.chick.app.distribution.dto.BranchReceiptItemRequest;
import com.chick.app.distribution.dto.BranchReceiptRequest;
import com.chick.app.distribution.dto.DistributionReverseRequest;
import com.chick.app.distribution.mapper.DistributionMapper;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.distribution.repository.ExpenseAllocationRepository;
import com.chick.app.expense.repository.ExpenseCategoryRepository;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.inventory.domain.InventoryMovement;
import com.chick.app.inventory.domain.StockBatch;
import com.chick.app.inventory.repository.InventoryMovementRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.margin.service.CompanyMarginRuleService;
import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.repository.BranchSettlementRepository;
import com.chick.app.sales.service.BranchSaleService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DistributionServiceTest {

	private final DistributionRepository distributions = mock(DistributionRepository.class);
	private final StockBatchRepository stockBatches = mock(StockBatchRepository.class);
	private final InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
	private final BranchSaleService branchSales = mock(BranchSaleService.class);
	private final BranchSettlementRepository settlements = mock(BranchSettlementRepository.class);

	private DistributionService service;
	private Distribution distribution;
	private DistributionItem line;
	private StockBatch batch;
	private ChickenType chickenType;

	@BeforeEach
	void setUp() {
		service = new DistributionService(
				distributions,
				mock(DistributionMapper.class),
				mock(BranchRepository.class),
				mock(ChickenTypeRepository.class),
				mock(ExpenseCategoryRepository.class),
				stockBatches,
				movements,
				mock(BranchReceiptRepository.class),
				mock(ExpenseAllocationRepository.class),
				mock(DocumentNumberService.class),
				mock(CompanyMarginRuleService.class),
				mock(BranchAccessService.class),
				branchSales,
				settlements);

		Branch branch = new Branch();
		branch.setId(UUID.randomUUID());

		chickenType = new ChickenType();
		chickenType.setId(UUID.randomUUID());

		batch = new StockBatch();
		batch.setId(UUID.randomUUID());
		batch.setChickenType(chickenType);
		batch.setRemainingWeightKg(new BigDecimal("0.000"));

		line = new DistributionItem();
		line.setId(UUID.randomUUID());
		line.setStockBatch(batch);
		line.setChickenType(chickenType);
		line.setWeightKg(new BigDecimal("100.000"));

		distribution = new Distribution();
		distribution.setId(UUID.randomUUID());
		distribution.setBranch(branch);
		distribution.setDistributionDate(LocalDate.of(2026, 11, 4));
		distribution.getItems().add(line);

		when(distributions.findById(distribution.getId())).thenReturn(Optional.of(distribution));
		when(distributions.save(any(Distribution.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(settlements.allocatedToDistribution(any(), any())).thenReturn(BigDecimal.ZERO);
	}

	@Test
	void receivingMustCoverEveryLine() {
		DistributionItem second = new DistributionItem();
		second.setId(UUID.randomUUID());
		second.setStockBatch(batch);
		second.setChickenType(chickenType);
		second.setWeightKg(new BigDecimal("50.000"));
		distribution.getItems().add(second);
		distribution.setStatus(DistributionStatus.DISPATCHED);

		BranchReceiptRequest request = new BranchReceiptRequest(Instant.now(), null, List.of(
				new BranchReceiptItemRequest(line.getId(), new BigDecimal("100.000"), null)));

		assertThatThrownBy(() -> service.receive(distribution.getId(), request))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Every distribution line");
		assertThat(distribution.getStatus()).isEqualTo(DistributionStatus.DISPATCHED);
	}

	@Test
	void reversingAReceivedDistributionReturnsOnlyTheReceivedWeight() {
		markReceived(new BigDecimal("95.000"));
		when(branchSales.availableWeightByChickenType(distribution.getBranch().getId()))
				.thenReturn(Map.of(chickenType.getId(), new BigDecimal("95.000")));

		service.reverse(distribution.getId(), new DistributionReverseRequest("wrong branch"));

		assertThat(batch.getRemainingWeightKg()).isEqualByComparingTo("95.000");
		ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
		verify(movements).save(movement.capture());
		assertThat(movement.getValue().getType()).isEqualTo(InventoryMovement.MovementType.REVERSAL);
		assertThat(movement.getValue().getWeightKg()).isEqualByComparingTo("95.000");
		assertThat(distribution.getStatus()).isEqualTo(DistributionStatus.REVERSED);
	}

	@Test
	void reversalIsRejectedOnceTheBranchHasClosedDaysWithTheStock() {
		markReceived(new BigDecimal("95.000"));
		when(branchSales.availableWeightByChickenType(distribution.getBranch().getId()))
				.thenReturn(Map.of(chickenType.getId(), new BigDecimal("40.000")));

		assertThatThrownBy(() -> service.reverse(distribution.getId(), new DistributionReverseRequest("late")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("closed days");
		assertThat(batch.getRemainingWeightKg()).isEqualByComparingTo("0.000");
		verify(movements, never()).save(any());
		assertThat(distribution.getStatus()).isEqualTo(DistributionStatus.RECEIVED);
	}

	@Test
	void reversalIsRejectedOncePaymentsAreRecordedAgainstTheDelivery() {
		markReceived(new BigDecimal("95.000"));
		when(settlements.allocatedToDistribution(eq(distribution.getId()), eq(BranchSettlement.Status.POSTED)))
				.thenReturn(new BigDecimal("5000.00"));

		assertThatThrownBy(() -> service.reverse(distribution.getId(), new DistributionReverseRequest("wrong branch")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("reverse them first");
		verify(movements, never()).save(any());
		assertThat(distribution.getStatus()).isEqualTo(DistributionStatus.RECEIVED);
	}

	@Test
	void reversingADispatchedDistributionReturnsTheFullWeight() {
		distribution.setStatus(DistributionStatus.DISPATCHED);

		service.reverse(distribution.getId(), new DistributionReverseRequest("cancelled"));

		assertThat(batch.getRemainingWeightKg()).isEqualByComparingTo("100.000");
		verify(branchSales, never()).availableWeightByChickenType(any());
	}

	private void markReceived(BigDecimal receivedWeight) {
		BranchReceipt receipt = new BranchReceipt();
		receipt.setDistribution(distribution);
		BranchReceiptItem receiptItem = new BranchReceiptItem();
		receiptItem.setBranchReceipt(receipt);
		receiptItem.setDistributionItem(line);
		receiptItem.setReceivedWeightKg(receivedWeight);
		receiptItem.setDiscrepancyWeightKg(line.getWeightKg().subtract(receivedWeight));
		receiptItem.setDiscrepancyReason(DiscrepancyReason.MELT_WATER);
		receipt.getItems().add(receiptItem);
		distribution.getReceipts().add(receipt);
		distribution.setStatus(DistributionStatus.RECEIVED);
	}
}
