package com.chick.app.sales.service;

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
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.ReceivedLine;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.payment.service.SettlementService;
import com.chick.app.pricing.dto.DailyPriceResponse;
import com.chick.app.pricing.service.DailyPriceService;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.dto.BranchSaleCreateRequest;
import com.chick.app.sales.dto.BranchSaleItemRequest;
import com.chick.app.sales.mapper.BranchSaleMapper;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BranchSaleServiceTest {

	private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

	private final BranchSaleRepository sales = mock(BranchSaleRepository.class);
	private final BranchRepository branches = mock(BranchRepository.class);
	private final ChickenTypeRepository chickenTypes = mock(ChickenTypeRepository.class);
	private final DailyPriceService dailyPrices = mock(DailyPriceService.class);
	private final DocumentNumberService documentNumbers = mock(DocumentNumberService.class);
	private final BranchReceiptRepository receipts = mock(BranchReceiptRepository.class);
	private final SettlementService settlements = mock(SettlementService.class);

	private BranchSaleService service;
	private Branch branch;
	private ChickenType chickenType;
	private final List<ReceivedLine> received = new ArrayList<>();
	private final List<Object[]> soldElsewhere = new ArrayList<>();

	@BeforeEach
	void setUp() {
		service = new BranchSaleService(sales, branches, chickenTypes, dailyPrices, mock(BranchSaleMapper.class),
				documentNumbers, mock(BranchAccessService.class), receipts, settlements);

		branch = new Branch();
		branch.setId(UUID.randomUUID());
		chickenType = new ChickenType();
		chickenType.setId(UUID.randomUUID());
		chickenType.setName("Broiler");

		when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
		when(branches.lockById(branch.getId())).thenReturn(Optional.of(branch));
		when(chickenTypes.findById(chickenType.getId())).thenReturn(Optional.of(chickenType));
		when(documentNumbers.next("BRANCH_SALE", "SALE")).thenReturn("SALE-2026-000001");
		when(sales.save(any(BranchSale.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sales.findByBranchAndSaleDate(eq(branch), any(LocalDate.class))).thenReturn(Optional.empty());
		when(receipts.receivedLines(eq(branch.getId()), any())).thenReturn(received);
		when(sales.soldWeightByChickenType(eq(branch.getId()), any())).thenReturn(soldElsewhere);
		when(settlements.settledAmount(any())).thenReturn(BigDecimal.ZERO);
		when(dailyPrices.current(eq(chickenType.getId()), eq(branch.getId()), any(), any()))
				.thenReturn(new DailyPriceResponse(
						UUID.randomUUID(), chickenType.getId(), branch.getId(), null,
						new BigDecimal("830.0000"), new BigDecimal("1000.0000"), Instant.now(), Instant.now()));
	}

	@Test
	void marksBranchProfitAgainstTransferPrice() {
		receive("400.000", "900.0000");

		BranchSale sale = record("100.000");

		assertThat(sale.getReference()).isEqualTo("SALE-2026-000001");
		assertThat(sale.getItems().get(0).getSellingPricePerKg()).isEqualByComparingTo("1000.0000");
		assertThat(sale.getItems().get(0).getTransferPricePerKg()).isEqualByComparingTo("900.0000");
		assertThat(sale.getTotalSales()).isEqualByComparingTo("100000.00");
		assertThat(sale.getTransferValue()).isEqualByComparingTo("90000.00");
		assertThat(sale.getBranchProfit()).isEqualByComparingTo("10000.00");
	}

	@Test
	void costsTheSaleAgainstTheOldestStockStillHeld() {
		receive("100.000", "900.0000");
		receive("100.000", "950.0000");
		soldElsewhere.add(new Object[] {chickenType.getId(), new BigDecimal("80.000")});

		BranchSale sale = record("50.000");

		// 20 kg left of the 900 layer, then 30 kg of the 950 layer.
		assertThat(sale.getTransferValue()).isEqualByComparingTo("46500.00");
		assertThat(sale.getItems().get(0).getTransferPricePerKg()).isEqualByComparingTo("930.0000");
	}

	@Test
	void linesForTheSameChickenTypeShareTheStock() {
		receive("100.000", "900.0000");

		assertThatThrownBy(() -> service.record(new BranchSaleCreateRequest(branch.getId(), DAY, null, List.of(
				new BranchSaleItemRequest(chickenType.getId(), new BigDecimal("60.000")),
				new BranchSaleItemRequest(chickenType.getId(), new BigDecimal("60.000"))))))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("available to sell");
	}

	@Test
	void rejectsSellingMoreThanTheBranchHasInStock() {
		receive("50.000", "900.0000");

		assertThatThrownBy(() -> record("100.000"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("available to sell");
	}

	@Test
	void reRecordingCannotDropBelowTheAmountAlreadySettled() {
		receive("400.000", "900.0000");
		BranchSale existing = new BranchSale();
		existing.setId(UUID.randomUUID());
		existing.setReference("SALE-2026-000009");
		when(sales.findByBranchAndSaleDate(eq(branch), eq(DAY))).thenReturn(Optional.of(existing));
		when(settlements.settledAmount(existing.getId())).thenReturn(new BigDecimal("90000.00"));

		assertThatThrownBy(() -> record("50.000"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("already have 90000.00 settled");
		verify(sales, never()).save(any());
	}

	private void receive(String weight, String transferPrice) {
		received.add(new ReceivedLine(chickenType.getId(), Instant.parse("2026-01-01T00:00:00Z").plusSeconds(received.size()),
				new BigDecimal(weight), new BigDecimal(transferPrice)));
	}

	private BranchSale record(String weight) {
		service.record(new BranchSaleCreateRequest(branch.getId(), DAY, null,
				List.of(new BranchSaleItemRequest(chickenType.getId(), new BigDecimal(weight)))));
		ArgumentCaptor<BranchSale> captor = ArgumentCaptor.forClass(BranchSale.class);
		verify(sales).save(captor.capture());
		return captor.getValue();
	}
}
