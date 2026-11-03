package com.chick.app.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.domain.BranchReceipt;
import com.chick.app.distribution.domain.BranchReceiptItem;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionItem;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.dto.BranchSaleCreateRequest;
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
	private DistributionRepository distributions;

	@Mock
	private BranchSaleMapper mapper;

	@Mock
	private DocumentNumberService documentNumbers;

	private BranchSaleService service;
	private Branch branch;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new BranchSaleService(sales, branches, distributions, mapper, documentNumbers);
		branch = new Branch();
		branch.setId(UUID.randomUUID());
		branch.setCode("BR-001");
		branch.setName("Main Branch");
		when(branches.findById(branch.getId())).thenReturn(Optional.of(branch));
		when(documentNumbers.next("BRANCH_SALE", "SALE")).thenReturn("SALE-2026-000001");
		when(sales.save(any(BranchSale.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void marksProfitAgainstCostOfStockReceivedThatDay() {
		when(distributions.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(receivedDistribution(new BigDecimal("398.000"))));
		when(sales.findByBranchAndSaleDate(eq(branch), any(LocalDate.class))).thenReturn(Optional.empty());

		service.record(new BranchSaleCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 15),
				new BigDecimal("400000.00"),
				"Daily counter sales"));

		ArgumentCaptor<BranchSale> captor = ArgumentCaptor.forClass(BranchSale.class);
		verify(sales).save(captor.capture());
		BranchSale sale = captor.getValue();
		assertThat(sale.getReference()).isEqualTo("SALE-2026-000001");
		assertThat(sale.getTotalSales()).isEqualByComparingTo("400000.00");
		assertThat(sale.getCostAmount()).isEqualByComparingTo("340528.80");
		assertThat(sale.getProfit()).isEqualByComparingTo("59471.20");
	}

	@Test
	void recordingTheSameDayAgainUpdatesTheExistingEntry() {
		when(distributions.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of(receivedDistribution(new BigDecimal("398.000"))));
		BranchSale existing = new BranchSale();
		existing.setId(UUID.randomUUID());
		existing.setReference("SALE-2026-000001");
		existing.setBranch(branch);
		existing.setSaleDate(LocalDate.of(2026, 1, 15));
		when(sales.findByBranchAndSaleDate(eq(branch), eq(LocalDate.of(2026, 1, 15))))
				.thenReturn(Optional.of(existing));

		service.record(new BranchSaleCreateRequest(
				branch.getId(),
				LocalDate.of(2026, 1, 15),
				new BigDecimal("410000.00"),
				null));

		ArgumentCaptor<BranchSale> captor = ArgumentCaptor.forClass(BranchSale.class);
		verify(sales).save(captor.capture());
		BranchSale sale = captor.getValue();
		assertThat(sale.getId()).isEqualTo(existing.getId());
		assertThat(sale.getReference()).isEqualTo("SALE-2026-000001");
		assertThat(sale.getTotalSales()).isEqualByComparingTo("410000.00");
		assertThat(sale.getProfit()).isEqualByComparingTo("69471.20");
	}

	private Distribution receivedDistribution(BigDecimal receivedWeightKg) {
		DistributionItem line = new DistributionItem();
		line.setWeightKg(new BigDecimal("400.000"));
		line.setUnitCost(new BigDecimal("855.6000"));

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
