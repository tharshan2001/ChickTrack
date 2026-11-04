package com.chick.app.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.chick.app.distribution.repository.DistributionRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class BranchSaleServiceTest {

	@Mock
	private BranchSaleRepository sales;

	@Mock
	private BranchRepository branches;

	@Mock
	private ChickenTypeRepository chickenTypes;

	@Mock
	private DistributionRepository distributions;

	@Mock
	private DailyPriceService dailyPrices;

	@Mock
	private BranchSaleMapper mapper;

	@Mock
	private DocumentNumberService documentNumbers;

	private BranchSaleService service;
	private Branch branch;
	private ChickenType chickenType;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new BranchSaleService(sales, branches, chickenTypes, distributions, dailyPrices, mapper,
				documentNumbers);

		branch = new Branch();
		branch.setId(UUID.randomUUID());
		branch.setCode("BR-001");
		branch.setName("Main Branch");

		chickenType = new ChickenType();
		chickenType.setId(UUID.randomUUID());
		chickenType.setName("Broiler");

		when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
		when(chickenTypes.findById(chickenType.getId())).thenReturn(Optional.of(chickenType));
		when(documentNumbers.next("BRANCH_SALE", "SALE")).thenReturn("SALE-2026-000001");
		when(sales.save(any(BranchSale.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(sales.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());
		when(dailyPrices.current(eq(chickenType.getId()), eq(branch.getId()), any(), any()))
				.thenReturn(new DailyPriceResponse(
						UUID.randomUUID(), chickenType.getId(), branch.getId(), null,
						new BigDecimal("830.0000"), new BigDecimal("1000.0000"), Instant.now(), Instant.now()));
	}

	@Test
	void marksBranchProfitAgainstTransferPrice() {
		when(distributions.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(receivedDistribution(new BigDecimal("400.000"))));
		when(sales.findByBranchAndSaleDate(eq(branch), any(LocalDate.class))).thenReturn(Optional.empty());

		service.record(new BranchSaleCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 15),
				"Daily counter sales",
				List.of(new BranchSaleItemRequest(chickenType.getId(), new BigDecimal("100.000")))));

		ArgumentCaptor<BranchSale> captor = ArgumentCaptor.forClass(BranchSale.class);
		verify(sales).save(captor.capture());
		BranchSale sale = captor.getValue();
		assertThat(sale.getReference()).isEqualTo("SALE-2026-000001");
		assertThat(sale.getItems()).hasSize(1);
		assertThat(sale.getItems().get(0).getSellingPricePerKg()).isEqualByComparingTo("1000.0000");
		assertThat(sale.getItems().get(0).getTransferPricePerKg()).isEqualByComparingTo("900.0000");
		assertThat(sale.getTotalSales()).isEqualByComparingTo("100000.00");
		assertThat(sale.getTransferValue()).isEqualByComparingTo("90000.00");
		assertThat(sale.getBranchProfit()).isEqualByComparingTo("10000.00");
	}

	@Test
	void rejectsSellingMoreThanTheBranchHasInStock() {
		when(distributions.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(receivedDistribution(new BigDecimal("50.000"))));

		assertThatThrownBy(() -> service.record(new BranchSaleCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 15),
				null,
				List.of(new BranchSaleItemRequest(chickenType.getId(), new BigDecimal("100.000"))))))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("available to sell");
	}

	private Distribution receivedDistribution(BigDecimal receivedWeightKg) {
		DistributionItem line = new DistributionItem();
		line.setChickenType(chickenType);
		line.setWeightKg(new BigDecimal("400.000"));
		line.setUnitCost(new BigDecimal("855.6000"));
		line.setTransferPricePerKg(new BigDecimal("900.0000"));

		BranchReceiptItem receiptItem = new BranchReceiptItem();
		receiptItem.setDistributionItem(line);
		receiptItem.setReceivedWeightKg(receivedWeightKg);

		BranchReceipt receipt = new BranchReceipt();
		receipt.setReceivedAt(Instant.parse("2026-01-15T10:00:00Z"));
		receipt.getItems().add(receiptItem);

		Distribution distribution = new Distribution();
		distribution.setBranch(branch);
		distribution.setStatus(DistributionStatus.RECEIVED);
		distribution.getReceipts().add(receipt);
		return distribution;
	}
}
