package com.chick.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chick.app.branch.dto.BranchCreateRequest;
import com.chick.app.branch.service.BranchService;
import com.chick.app.chicken.dto.ChickenTypeCreateRequest;
import com.chick.app.chicken.service.ChickenTypeService;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.dto.*;
import com.chick.app.distribution.domain.DiscrepancyReason;
import com.chick.app.distribution.service.DistributionService;
import com.chick.app.identity.dto.UserBranchesRequest;
import com.chick.app.identity.dto.PasswordResetRequest;
import com.chick.app.identity.dto.UserCreateRequest;
import com.chick.app.identity.dto.UserRolesRequest;
import com.chick.app.identity.dto.UserStatusRequest;
import com.chick.app.inventory.domain.StockAdjustment;
import com.chick.app.inventory.dto.StockAdjustmentRequest;
import com.chick.app.identity.repository.RoleRepository;
import com.chick.app.identity.repository.UserAccountRepository;
import com.chick.app.inventory.repository.StockBatchRepository;
import com.chick.app.payment.dto.*;
import com.chick.app.payment.service.SettlementService;
import com.chick.app.pricing.dto.DailyPriceCreateRequest;
import com.chick.app.pricing.service.DailyPriceService;
import com.chick.app.procurement.domain.CommissionMethod;
import com.chick.app.procurement.dto.*;
import com.chick.app.procurement.service.ProcurementService;
import com.chick.app.sales.dto.*;
import com.chick.app.sales.service.BranchSaleService;
import com.chick.app.supplier.dto.SupplierCreateRequest;
import com.chick.app.supplier.service.SupplierService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end flow against PostgreSQL: procurement, concurrent dispatch, receiving, sales,
 * reversal guard, settlement, branch scoping and document numbering under concurrency.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FlowIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired BranchService branches; @Autowired ChickenTypeService types; @Autowired SupplierService suppliers;
	@Autowired DailyPriceService prices; @Autowired ProcurementService procurements;
	@Autowired DistributionService distributions; @Autowired BranchSaleService sales;
	@Autowired SettlementService settlements; @Autowired StockBatchRepository batches;
	@Autowired DocumentNumberService numbers; @Autowired UserAccountRepository users; @Autowired RoleRepository roles;
	@Autowired TransactionTemplate tx;
	@Autowired com.chick.app.identity.service.UserService userService;
	@Autowired com.chick.app.inventory.service.InventoryService inventory;
	@Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
	@Autowired com.chick.app.expense.service.ExpenseCategoryService expenseCategories;
	@Autowired com.chick.app.expense.service.OperatingExpenseService operatingExpenses;
	@Autowired com.chick.app.report.service.ProfitLossService profitLoss;

	static void as(String user) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, "x", AuthorityUtils.NO_AUTHORITIES));
	}

	@AfterEach void clear() { SecurityContextHolder.clearContext(); }

	@Test
	void endToEnd() throws Exception {
		as("admin");
		String sfx = UUID.randomUUID().toString().substring(0, 6);
		UUID branch = branches.create(new BranchCreateRequest("B" + sfx, "Kandy " + sfx, null, null)).id();
		UUID other = branches.create(new BranchCreateRequest("O" + sfx, "Colombo " + sfx, null, null)).id();
		UUID type = types.create(new ChickenTypeCreateRequest("Broiler " + sfx, null)).id();
		UUID supplier = suppliers.create(new SupplierCreateRequest("Sup " + sfx, null, null, null, null, null)).id();
		prices.create(new DailyPriceCreateRequest(type, branch, null, new BigDecimal("800"), new BigDecimal("1000"),
				Instant.parse("2026-01-01T00:00:00Z")));

		procurements.create(new ProcurementCreateRequest(supplier, LocalDate.of(2026, 11, 4), null,
				List.of(new ProcurementItemRequest(type, new BigDecimal("100"), new BigDecimal("800"),
						CommissionMethod.FIXED, BigDecimal.ZERO, null)), null));

		// Two drafts over the same 100 kg created at the same time: the first reserves the stock,
		// the second is refused, so no draft ever promises weight another one holds.
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CyclicBarrier barrier = new CyclicBarrier(2);
		List<Future<Object>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(pool.submit(() -> {
				as("admin"); barrier.await();
				try {
					return distributions.create(new DistributionCreateRequest(branch, LocalDate.of(2026, 11, 4), null,
							List.of(new DistributionItemRequest(type, new BigDecimal("80"))), null));
				}
				catch (BusinessException e) { return e.getCode(); }
				catch (Exception e) { return e.getClass().getSimpleName(); }
			}));
		}
		List<Object> outcomes = new ArrayList<>();
		for (var f : results) outcomes.add(f.get());
		pool.shutdown();
		assertThat(outcomes).filteredOn(o -> o instanceof DistributionResponse).hasSize(1);
		assertThat(outcomes).contains("INSUFFICIENT_STOCK");
		var dispatched = (DistributionResponse) outcomes.stream().filter(o -> o instanceof DistributionResponse).findFirst().orElseThrow();
		UUID batchId = dispatched.items().get(0).stockBatchId();
		var held = batches.findById(batchId).orElseThrow();
		assertThat(held.getReservedWeightKg()).isEqualByComparingTo("80");
		assertThat(held.getRemainingWeightKg()).isEqualByComparingTo("100");

		// A draft that is reversed releases its reservation.
		var spare = distributions.create(new DistributionCreateRequest(branch, LocalDate.of(2026, 11, 4), null,
				List.of(new DistributionItemRequest(type, new BigDecimal("20"))), null));
		assertThat(batches.findById(batchId).orElseThrow().getReservedWeightKg()).isEqualByComparingTo("100");
		distributions.reverse(spare.id(), new DistributionReverseRequest("not needed"));
		assertThat(batches.findById(batchId).orElseThrow().getReservedWeightKg()).isEqualByComparingTo("80");

		distributions.dispatch(dispatched.id());
		held = batches.findById(batchId).orElseThrow();
		assertThat(held.getRemainingWeightKg()).isEqualByComparingTo("20");
		assertThat(held.getReservedWeightKg()).isEqualByComparingTo("0");
		var line = distributions.get(dispatched.id()).items().get(0);

		// Partial receipt rejected, full receipt with discrepancy accepted. Received on the 4th (Colombo).
		Instant receivedAt = Instant.parse("2026-11-04T06:30:00Z");
		assertThatThrownBy(() -> distributions.receive(dispatched.id(), new BranchReceiptRequest(receivedAt, null, List.of())))
				.isInstanceOf(Exception.class);
		distributions.receive(dispatched.id(), new BranchReceiptRequest(receivedAt, null,
				List.of(new BranchReceiptItemRequest(line.id(), new BigDecimal("78"), DiscrepancyReason.MELT_WATER))));

		// The branch owes for what it received: 78 kg x 800.
		assertThat(settlements.branchBalance(branch).outstandingAmount()).isEqualByComparingTo("62400.00");
		assertThat(settlements.openDeliveries(branch)).singleElement()
				.satisfies(open -> assertThat(open.outstandingAmount()).isEqualByComparingTo("62400.00"));

		// A closing count above what the branch holds is refused.
		assertThatThrownBy(() -> sales.record(new BranchSaleCreateRequest(branch, LocalDate.of(2026, 11, 4),
				new BigDecimal("50000"), null, List.of(new BranchSaleItemRequest(type, new BigDecimal("79"))))))
				.isInstanceOf(BusinessException.class).hasMessageContaining("more than");

		// Day close: 28 kg left of 78, so 50 kg sold; the branch took Rs 50,000.
		var sale = sales.record(new BranchSaleCreateRequest(branch, LocalDate.of(2026, 11, 4),
				new BigDecimal("50000"), null, List.of(new BranchSaleItemRequest(type, new BigDecimal("28")))));
		assertThat(sale.items().get(0).openingWeightKg()).isEqualByComparingTo("78");
		assertThat(sale.items().get(0).weightKg()).isEqualByComparingTo("50");
		assertThat(sale.transferValue()).isEqualByComparingTo("40000.00");
		assertThat(sale.branchProfit()).isEqualByComparingTo("10000.00");
		assertThat(sale.expectedSales()).isEqualByComparingTo("50000.00");
		assertThat(sale.variance()).isEqualByComparingTo("0.00");
		assertThat(sales.stock(branch, null)).singleElement()
				.satisfies(s -> assertThat(s.availableWeightKg()).isEqualByComparingTo("28"));

		// Reversal blocked: the branch closed a day with 50 of the 78 kg.
		assertThatThrownBy(() -> distributions.reverse(dispatched.id(), new DistributionReverseRequest("x")))
				.isInstanceOf(BusinessException.class).hasMessageContaining("closed days");

		// Payments go against the delivery; more than it owes is refused.
		assertThatThrownBy(() -> settlements.create(new BranchSettlementCreateRequest(branch, LocalDate.of(2026, 11, 4),
				new BigDecimal("70000.00"), "CASH", null, null,
				List.of(new BranchSettlementAllocationRequest(dispatched.id(), new BigDecimal("70000.00"))))))
				.isInstanceOf(BusinessException.class).hasMessageContaining("outstanding");
		settlements.create(new BranchSettlementCreateRequest(branch, LocalDate.of(2026, 11, 4), new BigDecimal("1000.00"),
				"CASH", null, null, List.of(new BranchSettlementAllocationRequest(dispatched.id(), new BigDecimal("1000.00")))));
		assertThat(settlements.branchBalance(branch).outstandingAmount()).isEqualByComparingTo("61400.00");

		// Procurements cannot be dated in the future.
		assertThatThrownBy(() -> procurements.create(new ProcurementCreateRequest(supplier,
				com.chick.app.common.time.BusinessTime.today().plusDays(1), null,
				List.of(new ProcurementItemRequest(type, new BigDecimal("10"), new BigDecimal("800"),
						CommissionMethod.FIXED, BigDecimal.ZERO, null)), null)))
				.isInstanceOf(BusinessException.class).hasMessageContaining("future");

		// Each document action leaves an audit record naming the actor.
		Integer audited = jdbc.queryForObject(
				"select count(*) from audit_logs where actor_username = 'admin' and entity_id = ? and action in ('DISTRIBUTION_CREATE','DISTRIBUTION_DISPATCH','DISTRIBUTION_RECEIVE')",
				Integer.class, dispatched.id().toString());
		assertThat(audited).isEqualTo(3);

		// A branch receiver with no branches sees nothing; with one branch sees only it.
		var receiver = userService.create(new UserCreateRequest("recv" + sfx, "receiver-pass-1", "Receiver", null, null,
				Set.of("BRANCH_RECEIVER"), Set.of(), false)).user();
		assertThat(receiver.roles()).containsExactly("BRANCH_RECEIVER");
		as("recv" + sfx);
		assertThat(sales.list(Pageable.unpaged(), null, null, null).getTotalElements()).isZero();
		assertThatThrownBy(() -> settlements.branchBalance(branch)).isInstanceOf(BusinessException.class);

		as("admin");
		userService.setBranches(receiver.id(), new UserBranchesRequest(Set.of(branch)));
		as("recv" + sfx);
		assertThat(sales.list(Pageable.unpaged(), null, null, null).getTotalElements()).isEqualTo(1);
		assertThatThrownBy(() -> sales.list(Pageable.unpaged(), other, null, null)).isInstanceOf(BusinessException.class);

		// The owner is the single main admin: it cannot be demoted, deactivated or given to anyone else.
		as("admin");
		UUID adminId = userService.me().id();
		assertThatThrownBy(() -> userService.setStatus(adminId, new UserStatusRequest(false)))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> userService.setRoles(adminId, new UserRolesRequest(Set.of("ACCOUNTANT"))))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> userService.resetPassword(adminId, new PasswordResetRequest(null, null)))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> userService.create(new UserCreateRequest("own2" + sfx, null, "Second owner", null, null,
				Set.of("OWNER"), Set.of(), null))).isInstanceOf(BusinessException.class).hasMessageContaining("OWNER");
		assertThatThrownBy(() -> userService.setRoles(receiver.id(), new UserRolesRequest(Set.of("OWNER"))))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> userService.create(new UserCreateRequest("RECV" + sfx, "another-pass", "Dup", null, null,
				Set.of("VIEWER"), Set.of(), null))).isInstanceOf(BusinessException.class).hasMessageContaining("exists");

		// Without a password the server generates one, returns it once and requires a change at first sign-in.
		var issued = userService.create(new UserCreateRequest("gen" + sfx, null, "Generated", null, null,
				Set.of("VIEWER"), Set.of(), null));
		assertThat(issued.temporaryPassword()).hasSize(12);
		assertThat(issued.user().mustChangePassword()).isTrue();
		var reset = userService.resetPassword(issued.user().id(), new PasswordResetRequest(null, null));
		assertThat(reset.temporaryPassword()).hasSize(12).isNotEqualTo(issued.temporaryPassword());
		assertThat(reset.user().mustChangePassword()).isTrue();

		// Warehouse adjustments change the batch and the ledger together.
		var damage = inventory.adjust(new StockAdjustmentRequest(batchId, StockAdjustment.Type.DAMAGE,
				new BigDecimal("5"), null, "Crate damage"));
		assertThat(damage.remainingWeightKg()).isEqualByComparingTo("15");
		assertThatThrownBy(() -> inventory.adjust(new StockAdjustmentRequest(batchId, StockAdjustment.Type.LOSS,
				new BigDecimal("16"), null, "Too much"))).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> inventory.adjust(new StockAdjustmentRequest(batchId, StockAdjustment.Type.ADJUSTMENT,
				new BigDecimal("1"), null, "No direction"))).isInstanceOf(BusinessException.class);
		inventory.adjust(new StockAdjustmentRequest(batchId, StockAdjustment.Type.ADJUSTMENT,
				new BigDecimal("2"), StockAdjustmentRequest.Direction.INCREASE, "Recount"));
		assertThat(inventory.getBatch(batchId).remainingWeightKg()).isEqualByComparingTo("17");
		assertThat(inventory.listMovements(Pageable.unpaged(), batchId, null, null, null).getTotalElements())
				.isEqualTo(5); // receipt, distribution, receiving loss, damage, adjustment
		assertThat(inventory.listMovements(Pageable.unpaged(), batchId, null, null, null).getContent())
				.extracting(m -> m.type()).contains("RECEIPT", "DISTRIBUTION", "LOSS", "DAMAGE", "ADJUSTMENT");

		// Profit and loss: 80 kg dispatched at 800/kg (no margin rule), 50 kg sold at 1000/kg, 2 kg lost
		// on receiving (at the 800 transfer price), 5 kg damaged in the warehouse (at the 800 landed cost).
		UUID rent = expenseCategories.create(new com.chick.app.expense.dto.ExpenseCategoryCreateRequest("Rent " + sfx, null)).id();
		UUID power = expenseCategories.create(new com.chick.app.expense.dto.ExpenseCategoryCreateRequest("Power " + sfx, null)).id();
		LocalDate day = LocalDate.of(2026, 11, 4);
		operatingExpenses.create(new com.chick.app.expense.dto.OperatingExpenseRequest(day, rent, branch,
				new BigDecimal("1500"), "Landlord", null));
		operatingExpenses.create(new com.chick.app.expense.dto.OperatingExpenseRequest(day, power, null,
				new BigDecimal("500"), null, "Head office power"));
		var mistake = operatingExpenses.create(new com.chick.app.expense.dto.OperatingExpenseRequest(day, power, null,
				new BigDecimal("999"), null, "Typed twice"));
		operatingExpenses.voidExpense(mistake.id(), new com.chick.app.expense.dto.OperatingExpenseVoidRequest("duplicate"));
		assertThatThrownBy(() -> operatingExpenses.create(new com.chick.app.expense.dto.OperatingExpenseRequest(
				com.chick.app.common.time.BusinessTime.today().plusDays(1), rent, null, BigDecimal.TEN, null, null)))
				.isInstanceOf(BusinessException.class);

		var all = profitLoss.profitLoss(profitLoss.resolve("custom", null, null, null, day,
				com.chick.app.common.time.BusinessTime.today(), null), null).summary();
		assertThat(all.headOffice().transferRevenue()).isEqualByComparingTo("64000.00");
		assertThat(all.headOffice().costOfGoods()).isEqualByComparingTo("64000.00");
		assertThat(all.headOffice().margin()).isEqualByComparingTo("0.00");
		assertThat(all.branches().sales()).isEqualByComparingTo("50000.00");
		assertThat(all.branches().profit()).isEqualByComparingTo("10000.00");
		assertThat(all.stockLosses().weightKg()).isEqualByComparingTo("7.000");
		assertThat(all.stockLosses().transitValue()).isEqualByComparingTo("1600.00");
		assertThat(all.stockLosses().warehouseValue()).isEqualByComparingTo("4000.00");
		assertThat(all.operatingExpenses().total()).isEqualByComparingTo("2000.00");
		assertThat(all.netProfit()).isEqualByComparingTo("2400.00");
		assertThat(all.netMarginPercent()).isEqualByComparingTo("4.80");

		// One branch: no warehouse losses and no head-office expenses.
		var kandyOnly = profitLoss.profitLoss(profitLoss.resolve("custom", null, null, null, day,
				com.chick.app.common.time.BusinessTime.today(), null), branch).summary();
		assertThat(kandyOnly.stockLosses().value()).isEqualByComparingTo("1600.00");
		assertThat(kandyOnly.operatingExpenses().total()).isEqualByComparingTo("1500.00");
		assertThat(kandyOnly.netProfit()).isEqualByComparingTo("6900.00");

		// Monthly view broken down by day; yearly by month.
		var november = profitLoss.profitLoss(profitLoss.resolve(null, null, "2026-11", null, null, null, null), null);
		assertThat(november.breakdown()).hasSize(30);
		var fourth = november.breakdown().stream().filter(b -> b.label().equals("2026-11-04")).findFirst().orElseThrow();
		assertThat(fourth.statement().branches().sales()).isEqualByComparingTo("50000.00");
		assertThat(fourth.statement().operatingExpenses().total()).isEqualByComparingTo("2000.00");
		var year = profitLoss.profitLoss(profitLoss.resolve("yearly", null, null, 2026, null, null, null), null);
		assertThat(year.breakdown()).hasSize(12);
		assertThat(year.breakdown().get(10).label()).isEqualTo("2026-11");
		assertThat(year.breakdown().get(10).statement().branches().profit()).isEqualByComparingTo("10000.00");
		assertThat(profitLoss.profitLoss(profitLoss.resolve("daily", day, null, null, null, null, null), null)
				.summary().branches().sales()).isEqualByComparingTo("50000.00");

		// Document numbers under concurrency for a fresh type.
		as("admin");
		String docType = "T" + sfx;
		ExecutorService p2 = Executors.newFixedThreadPool(8);
		List<Future<String>> nums = new ArrayList<>();
		for (int i = 0; i < 16; i++) nums.add(p2.submit(() -> numbers.next(docType, "T")));
		Set<String> unique = new HashSet<>();
		for (var f : nums) unique.add(f.get());
		p2.shutdown();
		assertThat(unique).hasSize(16);
	}

}
