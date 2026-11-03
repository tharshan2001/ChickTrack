package com.chick.app.pricing.service;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.pricing.domain.DailyPrice;
import com.chick.app.pricing.dto.DailyPriceCreateRequest;
import com.chick.app.pricing.dto.DailyPriceResponse;
import com.chick.app.pricing.mapper.DailyPriceMapper;
import com.chick.app.pricing.repository.DailyPriceRepository;
import com.chick.app.supplier.domain.Supplier;
import com.chick.app.supplier.repository.SupplierRepository;
import com.chick.app.common.exception.ResourceNotFoundException;
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
public class DailyPriceService {

	private final DailyPriceRepository dailyPrices;
	private final DailyPriceMapper mapper;
	private final ChickenTypeRepository chickenTypes;
	private final BranchRepository branches;
	private final SupplierRepository suppliers;

	public DailyPriceService(
			DailyPriceRepository dailyPrices,
			DailyPriceMapper mapper,
			ChickenTypeRepository chickenTypes,
			BranchRepository branches,
			SupplierRepository suppliers) {
		this.dailyPrices = dailyPrices;
		this.mapper = mapper;
		this.chickenTypes = chickenTypes;
		this.branches = branches;
		this.suppliers = suppliers;
	}

	@Transactional(readOnly = true)
	public Page<DailyPriceResponse> list(
			Pageable pageable,
			UUID chickenTypeId,
			UUID branchId,
			UUID supplierId,
			Instant from,
			Instant to) {
		Specification<DailyPrice> specification = (root, query, cb) -> cb.conjunction();
		if (chickenTypeId != null) {
			specification = specification.and((root, query, cb) ->
					cb.equal(root.get("chickenType").get("id"), chickenTypeId));
		}
		if (branchId != null) {
			specification = specification.and((root, query, cb) ->
					cb.equal(root.get("branch").get("id"), branchId));
		}
		if (supplierId != null) {
			specification = specification.and((root, query, cb) ->
					cb.equal(root.get("supplier").get("id"), supplierId));
		}
		if (from != null) {
			specification = specification.and((root, query, cb) ->
					cb.greaterThanOrEqualTo(root.get("effectiveFrom"), from));
		}
		if (to != null) {
			specification = specification.and((root, query, cb) ->
					cb.lessThan(root.get("effectiveFrom"), to));
		}
		return dailyPrices.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public DailyPriceResponse get(UUID id) {
		return mapper.toResponse(dailyPrices.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("DailyPrice", id)));
	}

	@Transactional
	public DailyPriceResponse create(DailyPriceCreateRequest request) {
		ChickenType chickenType = chickenTypes.findById(request.chickenTypeId())
				.orElseThrow(() -> new ResourceNotFoundException("ChickenType", request.chickenTypeId()));

		Branch branch = null;
		if (request.branchId() != null) {
			branch = branches.findById(request.branchId())
					.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));
		}

		Supplier supplier = null;
		if (request.supplierId() != null) {
			supplier = suppliers.findById(request.supplierId())
					.orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));
		}

		DailyPrice dailyPrice = mapper.toEntity(request);
		dailyPrice.setChickenType(chickenType);
		dailyPrice.setBranch(branch);
		dailyPrice.setSupplier(supplier);
		dailyPrice.setPurchasePricePerKg(request.purchasePricePerKg().setScale(4));
		dailyPrice.setSellingPricePerKg(request.sellingPricePerKg().setScale(4));
		return mapper.toResponse(dailyPrices.save(dailyPrice));
	}

	@Transactional(readOnly = true)
	public DailyPriceResponse current(UUID chickenTypeId, UUID branchId, UUID supplierId, Instant at) {
		Instant effectiveAt = (at == null) ? Instant.now() : at;

		List<DailyPrice> candidates = dailyPrices.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("chickenType").get("id"), chickenTypeId),
				cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveAt),
				branchPredicate(root, cb, branchId),
				supplierPredicate(root, cb, supplierId)));

		DailyPrice selected = candidates.stream()
				.max(Comparator
						.comparing(DailyPrice::getEffectiveFrom)
						.thenComparingInt(price -> scopeScore(price, branchId, supplierId)))
				.orElseThrow(() -> new ResourceNotFoundException("DailyPrice", "current price"));

		return mapper.toResponse(selected);
	}

	@Transactional(readOnly = true)
	public Page<DailyPriceResponse> trend(
			Pageable pageable,
			UUID chickenTypeId,
			UUID branchId,
			Instant from,
			Instant to) {
		return list(pageable, chickenTypeId, branchId, null, from, to);
	}

	private jakarta.persistence.criteria.Predicate branchPredicate(
			jakarta.persistence.criteria.Root<DailyPrice> root,
			jakarta.persistence.criteria.CriteriaBuilder cb,
			UUID branchId) {
		if (branchId == null) {
			return cb.isNull(root.get("branch"));
		}
		return cb.or(
				cb.equal(root.get("branch").get("id"), branchId),
				cb.isNull(root.get("branch")));
	}

	private jakarta.persistence.criteria.Predicate supplierPredicate(
			jakarta.persistence.criteria.Root<DailyPrice> root,
			jakarta.persistence.criteria.CriteriaBuilder cb,
			UUID supplierId) {
		if (supplierId == null) {
			return cb.isNull(root.get("supplier"));
		}
		return cb.or(
				cb.equal(root.get("supplier").get("id"), supplierId),
				cb.isNull(root.get("supplier")));
	}

	private int scopeScore(DailyPrice price, UUID branchId, UUID supplierId) {
		int score = 0;
		UUID priceBranchId = (price.getBranch() == null) ? null : price.getBranch().getId();
		UUID priceSupplierId = (price.getSupplier() == null) ? null : price.getSupplier().getId();
		if (Objects.equals(priceBranchId, branchId)) {
			score += 2;
		}
		if (Objects.equals(priceSupplierId, supplierId)) {
			score += 1;
		}
		return score;
	}
}
