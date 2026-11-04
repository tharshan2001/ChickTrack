package com.chick.app.margin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.margin.domain.CompanyMarginRule;
import com.chick.app.margin.mapper.CompanyMarginRuleMapper;
import com.chick.app.margin.repository.CompanyMarginRuleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CompanyMarginRuleServiceTest {

	@Mock
	private CompanyMarginRuleRepository rules;

	@Mock
	private CompanyMarginRuleMapper mapper;

	@Mock
	private ChickenTypeRepository chickenTypes;

	@Mock
	private BranchRepository branches;

	private CompanyMarginRuleService service;
	private ChickenType chickenType;
	private Branch branch;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new CompanyMarginRuleService(rules, mapper, chickenTypes, branches);
		chickenType = new ChickenType();
		chickenType.setId(UUID.randomUUID());
		chickenType.setName("Broiler");
		branch = new Branch();
		branch.setId(UUID.randomUUID());
		branch.setCode("BR-001");
	}

	@Test
	void prefersTheBranchSpecificRule() {
		CompanyMarginRule global = rule(null, "5.0000", Instant.parse("2026-01-01T00:00:00Z"));
		CompanyMarginRule branchRule = rule(branch, "8.0000", Instant.parse("2026-01-01T00:00:00Z"));
		when(rules.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(global, branchRule));

		BigDecimal percent = service.markupPercent(chickenType.getId(), branch.getId(),
				Instant.parse("2026-01-15T00:00:00Z"));

		assertThat(percent).isEqualByComparingTo("8.0000");
	}

	@Test
	void usesTheNewestEffectiveRule() {
		CompanyMarginRule older = rule(null, "5.0000", Instant.parse("2026-01-01T00:00:00Z"));
		CompanyMarginRule newer = rule(null, "6.5000", Instant.parse("2026-02-01T00:00:00Z"));
		when(rules.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(older, newer));

		BigDecimal percent = service.markupPercent(chickenType.getId(), branch.getId(),
				Instant.parse("2026-02-05T00:00:00Z"));

		assertThat(percent).isEqualByComparingTo("6.5000");
	}

	@Test
	void defaultsToZeroWithoutARule() {
		when(rules.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

		BigDecimal percent = service.markupPercent(chickenType.getId(), branch.getId(), Instant.now());

		assertThat(percent).isEqualByComparingTo("0.0000");
	}

	private CompanyMarginRule rule(Branch scope, String percent, Instant effectiveFrom) {
		CompanyMarginRule rule = new CompanyMarginRule();
		rule.setChickenType(chickenType);
		rule.setBranch(scope);
		rule.setMarkupPercent(new BigDecimal(percent));
		rule.setEffectiveFrom(effectiveFrom);
		return rule;
	}
}
