package com.chick.app.margin.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.margin.domain.CompanyMarginRule;
import com.chick.app.margin.dto.CompanyMarginRuleCreateRequest;
import com.chick.app.margin.dto.CompanyMarginRuleResponse;
import com.chick.app.margin.mapper.CompanyMarginRuleMapper;
import com.chick.app.margin.repository.CompanyMarginRuleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyMarginRuleService {

	private static final int PERCENT_SCALE = 4;

	private final CompanyMarginRuleRepository rules;
	private final CompanyMarginRuleMapper mapper;
	private final ChickenTypeRepository chickenTypes;
	private final BranchRepository branches;

	public CompanyMarginRuleService(
			CompanyMarginRuleRepository rules,
			CompanyMarginRuleMapper mapper,
			ChickenTypeRepository chickenTypes,
			BranchRepository branches) {
		this.rules = rules;
		this.mapper = mapper;
		this.chickenTypes = chickenTypes;
		this.branches = branches;
	}

	@Transactional
	@Audited(action = "MARGIN_RULE_CREATE", entityType = "CompanyMarginRule")
	public CompanyMarginRuleResponse create(CompanyMarginRuleCreateRequest request) {
		ChickenType chickenType = chickenTypes.findById(request.chickenTypeId())
				.orElseThrow(() -> new ResourceNotFoundException("ChickenType", request.chickenTypeId()));
		Branch branch = null;
		if (request.branchId() != null) {
			branch = branches.findById(request.branchId())
					.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));
		}

		CompanyMarginRule rule = new CompanyMarginRule();
		rule.setChickenType(chickenType);
		rule.setBranch(branch);
		rule.setMarkupPercent(request.markupPercent().setScale(PERCENT_SCALE, java.math.RoundingMode.HALF_UP));
		rule.setEffectiveFrom(request.effectiveFrom());
		return mapper.toResponse(rules.save(rule));
	}

	@Transactional(readOnly = true)
	public Page<CompanyMarginRuleResponse> list(Pageable pageable, UUID chickenTypeId, UUID branchId) {
		Specification<CompanyMarginRule> specification = (root, query, cb) -> cb.conjunction();
		if (chickenTypeId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("chickenType").get("id"), chickenTypeId));
		}
		if (branchId != null) {
			specification = specification.and(
					(root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		return rules.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public CompanyMarginRuleResponse get(UUID id) {
		return mapper.toResponse(rules.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("CompanyMarginRule", id)));
	}

	@Transactional(readOnly = true)
	public BigDecimal markupPercent(UUID chickenTypeId, UUID branchId, Instant at) {
		Instant effectiveAt = at == null ? Instant.now() : at;
		List<CompanyMarginRule> candidates = rules.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("chickenType").get("id"), chickenTypeId),
				cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveAt),
				cb.or(
						cb.equal(root.get("branch").get("id"), branchId),
						cb.isNull(root.get("branch")))));

		return candidates.stream()
				.max(Comparator
						.comparing(CompanyMarginRule::getEffectiveFrom)
						.thenComparingInt(rule -> scopeScore(rule, branchId)))
				.map(rule -> rule.getMarkupPercent().setScale(PERCENT_SCALE, java.math.RoundingMode.HALF_UP))
				.orElseGet(() -> BigDecimal.ZERO.setScale(PERCENT_SCALE, java.math.RoundingMode.HALF_UP));
	}

	private int scopeScore(CompanyMarginRule rule, UUID branchId) {
		UUID ruleBranchId = rule.getBranch() == null ? null : rule.getBranch().getId();
		return Objects.equals(ruleBranchId, branchId) ? 1 : 0;
	}
}
