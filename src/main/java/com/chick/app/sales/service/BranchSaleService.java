package com.chick.app.sales.service;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.dto.BranchSaleCreateRequest;
import com.chick.app.sales.dto.BranchSaleResponse;
import com.chick.app.sales.mapper.BranchSaleMapper;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchSaleService {

	private static final int MONEY_SCALE = 2;

	private static final EnumSet<DistributionStatus> RECEIVED_STATUSES =
			EnumSet.of(DistributionStatus.RECEIVED, DistributionStatus.CLOSED);

	private final BranchSaleRepository sales;
	private final BranchRepository branches;
	private final DistributionRepository distributions;
	private final BranchSaleMapper mapper;
	private final DocumentNumberService documentNumbers;

	public BranchSaleService(
			BranchSaleRepository sales,
			BranchRepository branches,
			DistributionRepository distributions,
			BranchSaleMapper mapper,
			DocumentNumberService documentNumbers) {
		this.sales = sales;
		this.branches = branches;
		this.distributions = distributions;
		this.mapper = mapper;
		this.documentNumbers = documentNumbers;
	}

	@Transactional
	public BranchSaleResponse record(BranchSaleCreateRequest request) {
		Branch branch = branches.findById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		BigDecimal totalSales = request.totalSales().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		BigDecimal cost = costOfStockReceived(branch, request.saleDate());
		BigDecimal profit = totalSales.subtract(cost);

		BranchSale sale = sales.findByBranchAndSaleDate(branch, request.saleDate())
				.orElseGet(BranchSale::new);
		if (sale.getId() == null) {
			sale.setReference(documentNumbers.next("BRANCH_SALE", "SALE"));
		}
		sale.setBranch(branch);
		sale.setSaleDate(request.saleDate());
		sale.setTotalSales(totalSales);
		sale.setCostAmount(cost);
		sale.setProfit(profit);
		sale.setNotes(request.notes());

		return mapper.toResponse(sales.save(sale));
	}

	@Transactional(readOnly = true)
	public Page<BranchSaleResponse> list(Pageable pageable, UUID branchId, LocalDate from, LocalDate to) {
		Specification<BranchSale> specification = (root, query, cb) -> cb.conjunction();
		if (branchId != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		if (from != null) {
			specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("saleDate"), from));
		}
		if (to != null) {
			specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("saleDate"), to));
		}
		return sales.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public BranchSaleResponse get(UUID id) {
		BranchSale sale = sales.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("BranchSale", id));
		return mapper.toResponse(sale);
	}

	@Transactional(readOnly = true)
	public BigDecimal costOfStockReceived(Branch branch, LocalDate date) {
		Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		Specification<Distribution> specification = (root, query, cb) -> cb.and(
				cb.equal(root.get("branch").get("id"), branch.getId()),
				root.get("status").in(RECEIVED_STATUSES));

		BigDecimal cost = BigDecimal.ZERO;
		for (Distribution distribution : distributions.findAll(specification)) {
			for (var receipt : distribution.getReceipts()) {
				if (receipt.getReceivedAt().isBefore(start) || !receipt.getReceivedAt().isBefore(end)) {
					continue;
				}
				for (var receiptItem : receipt.getItems()) {
					BigDecimal unitCost = receiptItem.getDistributionItem().getUnitCost();
					cost = cost.add(receiptItem.getReceivedWeightKg()
							.multiply(unitCost)
							.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				}
			}
		}
		return cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}
}
