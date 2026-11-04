package com.chick.app.sales.service;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.DistributionRepository;
import com.chick.app.pricing.dto.DailyPriceResponse;
import com.chick.app.pricing.service.DailyPriceService;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.domain.BranchSaleItem;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchSaleService {

	private static final int MONEY_SCALE = 2;
	private static final int COST_SCALE = 4;
	private static final int WEIGHT_SCALE = 3;

	private static final EnumSet<DistributionStatus> RECEIVED_STATUSES =
			EnumSet.of(DistributionStatus.RECEIVED, DistributionStatus.CLOSED);

	private final BranchSaleRepository sales;
	private final BranchRepository branches;
	private final ChickenTypeRepository chickenTypes;
	private final DistributionRepository distributions;
	private final DailyPriceService dailyPrices;
	private final BranchSaleMapper mapper;
	private final DocumentNumberService documentNumbers;

	public BranchSaleService(
			BranchSaleRepository sales,
			BranchRepository branches,
			ChickenTypeRepository chickenTypes,
			DistributionRepository distributions,
			DailyPriceService dailyPrices,
			BranchSaleMapper mapper,
			DocumentNumberService documentNumbers) {
		this.sales = sales;
		this.branches = branches;
		this.chickenTypes = chickenTypes;
		this.distributions = distributions;
		this.dailyPrices = dailyPrices;
		this.mapper = mapper;
		this.documentNumbers = documentNumbers;
	}

	@Transactional
	public BranchSaleResponse record(BranchSaleCreateRequest request) {
		Branch branch = branches.findById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		BranchSale sale = sales.findByBranchAndSaleDate(branch, request.saleDate())
				.orElseGet(BranchSale::new);
		if (sale.getId() == null) {
			sale.setReference(documentNumbers.next("BRANCH_SALE", "SALE"));
		}
		sale.setBranch(branch);
		sale.setSaleDate(request.saleDate());
		sale.setNotes(request.notes());
		sale.getItems().clear();

		Map<UUID, BranchStock> stock = branchStock(branch, sale.getId());
		Instant effectiveAt = request.saleDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal transferValue = BigDecimal.ZERO;

		for (var itemRequest : request.items()) {
			ChickenType chickenType = chickenTypes.findById(itemRequest.chickenTypeId())
					.orElseThrow(() -> new ResourceNotFoundException("ChickenType", itemRequest.chickenTypeId()));
			BigDecimal weight = itemRequest.weightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);

			BranchStock chickenStock = stock.get(chickenType.getId());
			BigDecimal available = chickenStock == null ? BigDecimal.ZERO : chickenStock.availableWeight();
			if (weight.compareTo(available) > 0) {
				throw new BusinessException("INSUFFICIENT_BRANCH_STOCK",
						"Branch has " + available.stripTrailingZeros().toPlainString()
								+ " kg of " + chickenType.getName() + " available to sell",
						HttpStatus.CONFLICT);
			}

			BigDecimal sellingPrice = resolveSellingPrice(chickenType.getId(), branch.getId(), effectiveAt);
			BigDecimal transferPrice = chickenStock == null
					? BigDecimal.ZERO.setScale(COST_SCALE)
					: chickenStock.transferPricePerKg();

			BranchSaleItem item = new BranchSaleItem();
			item.setBranchSale(sale);
			item.setChickenType(chickenType);
			item.setWeightKg(weight);
			item.setSellingPricePerKg(sellingPrice);
			item.setTransferPricePerKg(transferPrice);
			item.setRevenue(weight.multiply(sellingPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setCost(weight.multiply(transferPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setProfit(item.getRevenue().subtract(item.getCost()));
			sale.getItems().add(item);

			totalSales = totalSales.add(item.getRevenue());
			transferValue = transferValue.add(item.getCost());
		}

		sale.setTotalSales(totalSales.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		sale.setTransferValue(transferValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		sale.setBranchProfit(totalSales.subtract(transferValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP));

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

	private BigDecimal resolveSellingPrice(UUID chickenTypeId, UUID branchId, Instant effectiveAt) {
		DailyPriceResponse price;
		try {
			price = dailyPrices.current(chickenTypeId, branchId, null, effectiveAt);
		}
		catch (ResourceNotFoundException ex) {
			throw new BusinessException("PRICE_NOT_CONFIGURED",
					"No branch selling price is configured for chicken type " + chickenTypeId
							+ " on " + effectiveAt,
					HttpStatus.BAD_REQUEST);
		}
		return price.sellingPricePerKg();
	}

	private Map<UUID, BranchStock> branchStock(Branch branch, UUID excludedSaleId) {
		Map<UUID, BranchStock> stock = new HashMap<>();

		Specification<Distribution> specification = (root, query, cb) -> cb.and(
				cb.equal(root.get("branch").get("id"), branch.getId()),
				root.get("status").in(RECEIVED_STATUSES));
		for (Distribution distribution : distributions.findAll(specification)) {
			for (var receipt : distribution.getReceipts()) {
				for (var receiptItem : receipt.getItems()) {
					var line = receiptItem.getDistributionItem();
					BranchStock chickenStock = stock.computeIfAbsent(
							line.getChickenType().getId(),
							id -> new BranchStock());
					chickenStock.receivedWeight = chickenStock.receivedWeight.add(receiptItem.getReceivedWeightKg());
					chickenStock.transferValue = chickenStock.transferValue.add(receiptItem.getReceivedWeightKg()
							.multiply(line.getTransferPricePerKg())
							.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				}
			}
		}

		Specification<BranchSale> salesSpec =
				(root, query, cb) -> cb.equal(root.get("branch").get("id"), branch.getId());
		for (BranchSale sale : sales.findAll(salesSpec)) {
			if (excludedSaleId != null && excludedSaleId.equals(sale.getId())) {
				continue;
			}
			for (BranchSaleItem item : sale.getItems()) {
				BranchStock chickenStock = stock.computeIfAbsent(
						item.getChickenType().getId(),
						id -> new BranchStock());
				chickenStock.soldWeight = chickenStock.soldWeight.add(item.getWeightKg());
			}
		}

		return stock;
	}

	private static final class BranchStock {
		BigDecimal receivedWeight = BigDecimal.ZERO;
		BigDecimal transferValue = BigDecimal.ZERO;
		BigDecimal soldWeight = BigDecimal.ZERO;

		BigDecimal availableWeight() {
			return receivedWeight.subtract(soldWeight).setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
		}

		BigDecimal transferPricePerKg() {
			if (receivedWeight.compareTo(BigDecimal.ZERO) == 0) {
				return BigDecimal.ZERO.setScale(COST_SCALE);
			}
			return transferValue.divide(receivedWeight, COST_SCALE, RoundingMode.HALF_UP);
		}
	}
}
