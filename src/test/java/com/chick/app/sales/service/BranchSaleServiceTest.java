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
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.ReceivedLine;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.pricing.dto.DailyPriceResponse;
import com.chick.app.pricing.service.DailyPriceService;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.domain.BranchSaleItem;
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

	private BranchSaleService service;
	private Branch branch;
	private ChickenType broiler;
	private final List<ReceivedLine> received = new ArrayList<>();
	private final List<Object[]> soldBefore = new ArrayList<>();

	@BeforeEach
	void setUp() {
		service = new BranchSaleService(sales, branches, chickenTypes, dailyPrices, mock(BranchSaleMapper.class),
				documentNumbers, mock(BranchAccessService.class), receipts);

		branch = new Branch();
		branch.setId(UUID.randomUUID());
		broiler = chickenType("Broiler");

		when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
		when(branches.lockById(branch.getId())).thenReturn(Optional.of(branch));
		when(documentNumbers.next("BRANCH_SALE", "SALE")).thenReturn("SALE-2026-000001");
		when(sales.save(any(BranchSale.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sales.findByBranchAndSaleDate(eq(branch), any(LocalDate.class))).thenReturn(Optional.empty());
		when(sales.findFirstByBranchIdAndSaleDateAfterOrderBySaleDateDesc(eq(branch.getId()), any()))
				.thenReturn(Optional.empty());
		when(receipts.receivedLines(eq(branch.getId()), any())).thenReturn(received);
		when(sales.soldWeightBefore(eq(branch.getId()), any())).thenReturn(soldBefore);
		price(broiler, "1000.0000");
	}

	@Test
	void soldWeightIsOpeningMinusClosingAndProfitIsTakingsMinusItsCost() {
		receive(broiler, "100.000", "900.0000");

		BranchSale sale = close("68000", line(broiler, "30.000"));

		BranchSaleItem item = sale.getItems().get(0);
		assertThat(sale.getReference()).isEqualTo("SALE-2026-000001");
		assertThat(item.getOpeningWeightKg()).isEqualByComparingTo("100.000");
		assertThat(item.getClosingWeightKg()).isEqualByComparingTo("30.000");
		assertThat(item.getWeightKg()).isEqualByComparingTo("70.000");
		assertThat(item.getTransferPricePerKg()).isEqualByComparingTo("900.0000");
		assertThat(sale.getTotalSales()).isEqualByComparingTo("68000.00");
		assertThat(sale.getTransferValue()).isEqualByComparingTo("63000.00");
		assertThat(sale.getBranchProfit()).isEqualByComparingTo("5000.00");
		// Expected at the branch price: 70 kg x 1000 = 70,000, so the branch is 2,000 short.
		assertThat(sale.getExpectedSales()).isEqualByComparingTo("70000.00");
	}

	@Test
	void costsTheSoldWeightAgainstTheOldestStockStillHeld() {
		receive(broiler, "100.000", "900.0000");
		receive(broiler, "100.000", "950.0000");
		soldBefore.add(new Object[] {broiler.getId(), new BigDecimal("80.000")});

		// Opening 120 kg (20 of the 900 layer, 100 of the 950 layer); 70 left, so 50 sold.
		BranchSale sale = close("50000", line(broiler, "70.000"));

		assertThat(sale.getItems().get(0).getOpeningWeightKg()).isEqualByComparingTo("120.000");
		assertThat(sale.getTransferValue()).isEqualByComparingTo("46500.00");
		assertThat(sale.getItems().get(0).getTransferPricePerKg()).isEqualByComparingTo("930.0000");
	}

	@Test
	void stockReceivedAfterTheDayIsNotPartOfItsOpeningStock() {
		receive(broiler, "100.000", "900.0000");
		received.add(new ReceivedLine(broiler.getId(), Instant.parse("2026-01-20T05:00:00Z"),
				new BigDecimal("50.000"), new BigDecimal("950.0000")));

		BranchSale sale = close("10000", line(broiler, "90.000"));

		assertThat(sale.getItems().get(0).getOpeningWeightKg()).isEqualByComparingTo("100.000");
		assertThat(sale.getItems().get(0).getWeightKg()).isEqualByComparingTo("10.000");
	}

	@Test
	void rejectsAClosingStockAboveWhatTheBranchHeld() {
		receive(broiler, "50.000", "900.0000");

		assertThatThrownBy(() -> close("1000", line(broiler, "60.000")))
				.isInstanceOf(BusinessException.class)
				.satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CLOSING_ABOVE_STOCK"));
		verify(sales, never()).save(any());
	}

	@Test
	void everyChickenTypeWithStockNeedsAClosingCount() {
		ChickenType country = chickenType("Country chicken");
		receive(broiler, "50.000", "900.0000");
		receive(country, "10.000", "1500.0000");

		assertThatThrownBy(() -> close("1000", line(broiler, "40.000")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Country chicken");
	}

	@Test
	void onlyTheLatestDayCanBeChanged() {
		receive(broiler, "50.000", "900.0000");
		BranchSale later = new BranchSale();
		later.setSaleDate(DAY.plusDays(1));
		when(sales.findFirstByBranchIdAndSaleDateAfterOrderBySaleDateDesc(branch.getId(), DAY))
				.thenReturn(Optional.of(later));

		assertThatThrownBy(() -> close("1000", line(broiler, "40.000")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("only the latest day");
		verify(sales, never()).save(any());
	}

	@Test
	void expectedSalesAreUnknownWhenASoldTypeHasNoPrice() {
		ChickenType country = chickenType("Country chicken");
		receive(country, "10.000", "1500.0000");
		when(dailyPrices.current(eq(country.getId()), eq(branch.getId()), any(), any()))
				.thenThrow(new ResourceNotFoundException("DailyPrice", "current price"));

		BranchSale sale = close("9000", line(country, "5.000"));

		assertThat(sale.getExpectedSales()).isNull();
		assertThat(sale.getItems().get(0).getSellingPricePerKg()).isNull();
		assertThat(sale.getBranchProfit()).isEqualByComparingTo("1500.00");
	}

	@Test
	void savingTheSameDayAgainKeepsItsReference() {
		receive(broiler, "50.000", "900.0000");
		BranchSale existing = new BranchSale();
		existing.setId(UUID.randomUUID());
		existing.setReference("SALE-2026-000009");
		when(sales.findByBranchAndSaleDate(branch, DAY)).thenReturn(Optional.of(existing));

		BranchSale sale = close("9000", line(broiler, "40.000"));

		assertThat(sale.getReference()).isEqualTo("SALE-2026-000009");
		verify(documentNumbers, never()).next(any(), any());
	}

	private ChickenType chickenType(String name) {
		ChickenType type = new ChickenType();
		type.setId(UUID.randomUUID());
		type.setName(name);
		when(chickenTypes.findById(type.getId())).thenReturn(Optional.of(type));
		return type;
	}

	private void price(ChickenType type, String sellingPrice) {
		when(dailyPrices.current(eq(type.getId()), eq(branch.getId()), any(), any()))
				.thenReturn(new DailyPriceResponse(
						UUID.randomUUID(), type.getId(), branch.getId(), null,
						new BigDecimal("830.0000"), new BigDecimal(sellingPrice), Instant.now(), Instant.now()));
	}

	private void receive(ChickenType type, String weight, String transferPrice) {
		received.add(new ReceivedLine(type.getId(), Instant.parse("2026-01-01T00:00:00Z").plusSeconds(received.size()),
				new BigDecimal(weight), new BigDecimal(transferPrice)));
	}

	private static BranchSaleItemRequest line(ChickenType type, String closingWeight) {
		return new BranchSaleItemRequest(type.getId(), new BigDecimal(closingWeight));
	}

	private BranchSale close(String totalSales, BranchSaleItemRequest... lines) {
		service.record(new BranchSaleCreateRequest(branch.getId(), DAY, new BigDecimal(totalSales), null, List.of(lines)));
		ArgumentCaptor<BranchSale> captor = ArgumentCaptor.forClass(BranchSale.class);
		verify(sales).save(captor.capture());
		return captor.getValue();
	}
}
