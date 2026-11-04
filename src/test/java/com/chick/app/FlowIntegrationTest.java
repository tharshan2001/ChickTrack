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
import com.chick.app.identity.domain.UserAccount;
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
	@Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

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

		// Two drafts over the same 100 kg, dispatched at the same time: exactly one may succeed.
		var d1 = distributions.create(new DistributionCreateRequest(branch, LocalDate.of(2026, 11, 4), null,
				List.of(new DistributionItemRequest(type, new BigDecimal("80"))), null));
		var d2 = distributions.create(new DistributionCreateRequest(branch, LocalDate.of(2026, 11, 4), null,
				List.of(new DistributionItemRequest(type, new BigDecimal("80"))), null));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CyclicBarrier barrier = new CyclicBarrier(2);
		List<Future<String>> results = new ArrayList<>();
		for (var d : List.of(d1, d2)) {
			results.add(pool.submit(() -> {
				as("admin"); barrier.await();
				try { distributions.dispatch(d.id()); return "OK"; }
				catch (BusinessException e) { return e.getCode(); }
				catch (Exception e) { return e.getClass().getSimpleName(); }
			}));
		}
		List<String> outcomes = new ArrayList<>();
		for (var f : results) outcomes.add(f.get());
		pool.shutdown();
		assertThat(outcomes).containsOnlyOnce("OK");
		UUID batchId = d1.items().get(0).stockBatchId();
		assertThat(batches.findById(batchId).orElseThrow().getRemainingWeightKg()).isEqualByComparingTo("20");

		var dispatched = outcomes.get(0).equals("OK") ? d1 : d2;
		var line = distributions.get(dispatched.id()).items().get(0);

		// Partial receipt rejected, full receipt with discrepancy accepted.
		assertThatThrownBy(() -> distributions.receive(dispatched.id(), new BranchReceiptRequest(Instant.now(), null, List.of())))
				.isInstanceOf(Exception.class);
		distributions.receive(dispatched.id(), new BranchReceiptRequest(Instant.now(), null,
				List.of(new BranchReceiptItemRequest(line.id(), new BigDecimal("78"), DiscrepancyReason.MELT_WATER))));

		var sale = sales.record(new BranchSaleCreateRequest(branch, LocalDate.of(2026, 11, 4), null,
				List.of(new BranchSaleItemRequest(type, new BigDecimal("50")))));
		assertThat(sale.transferValue()).isEqualByComparingTo("40000.00");

		// Reversal blocked: branch sold 50 of 78.
		assertThatThrownBy(() -> distributions.reverse(dispatched.id(), new DistributionReverseRequest("x")))
				.isInstanceOf(BusinessException.class).hasMessageContaining("already sold");

		settlements.create(new BranchSettlementCreateRequest(branch, LocalDate.of(2026, 11, 4), new BigDecimal("1000.00"),
				"CASH", null, null, List.of(new BranchSettlementAllocationRequest(sale.id(), new BigDecimal("1000.00")))));

		// Each document action leaves an audit record naming the actor.
		Integer audited = jdbc.queryForObject(
				"select count(*) from audit_logs where actor_username = 'admin' and entity_id = ? and action in ('DISTRIBUTION_CREATE','DISTRIBUTION_DISPATCH','DISTRIBUTION_RECEIVE')",
				Integer.class, dispatched.id().toString());
		assertThat(audited).isEqualTo(3);

		// A branch receiver with no branches sees nothing; with one branch sees only it.
		UserAccount receiver = new UserAccount();
		receiver.setUsername("recv" + sfx); receiver.setPasswordHash("{noop}x"); receiver.setFullName("R");
		receiver.getRoles().add(roles.findByName("BRANCH_RECEIVER").orElseThrow());
		users.save(receiver);
		as("recv" + sfx);
		assertThat(sales.list(Pageable.unpaged(), null, null, null).getTotalElements()).isZero();
		assertThatThrownBy(() -> settlements.branchBalance(branch)).isInstanceOf(BusinessException.class);

		as("admin");
		tx.executeWithoutResult(s -> {
			UserAccount r = users.findByUsername("recv" + sfx).orElseThrow();
			r.getBranches().add(entityRef(branch));
		});
		as("recv" + sfx);
		assertThat(sales.list(Pageable.unpaged(), null, null, null).getTotalElements()).isEqualTo(1);
		assertThatThrownBy(() -> sales.list(Pageable.unpaged(), other, null, null)).isInstanceOf(BusinessException.class);

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

	@Autowired jakarta.persistence.EntityManager em;
	com.chick.app.branch.domain.Branch entityRef(UUID id) { return em.getReference(com.chick.app.branch.domain.Branch.class, id); }
}
