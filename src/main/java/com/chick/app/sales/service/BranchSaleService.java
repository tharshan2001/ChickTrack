package com.chick.app.sales.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.ReceivedLine;
import com.chick.app.payment.service.SettlementService;
import java.util.ArrayList;
import java.util.List;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.pricing.dto.DailyPriceResponse;
import com.chick.app.pricing.service.DailyPriceService;
import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.domain.BranchSaleItem;
import com.chick.app.sales.dto.BranchSaleCreateRequest;
import com.chick.app.sales.dto.BranchSaleResponse;
import com.chick.app.sales.dto.BranchStockResponse;
import com.chick.app.sales.mapper.BranchSaleMapper;
import com.chick.app.sales.repository.BranchSaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import com.chick.app.common.time.BusinessTime;
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
	private final DailyPriceService dailyPrices;
	private final BranchSaleMapper mapper;
	private final DocumentNumberService documentNumbers;
	private final BranchAccessService branchAccess;
	private final BranchReceiptRepository receipts;
	private final SettlementService settlements;

	public BranchSaleService(
			BranchSaleRepository sales,
			BranchRepository branches,
			ChickenTypeRepository chickenTypes,
			DailyPriceService dailyPrices,
			BranchSaleMapper mapper,
			DocumentNumberService documentNumbers,
			BranchAccessService branchAccess,
			BranchReceiptRepository receipts,
			SettlementService settlements) {
		this.sales = sales;
		this.branches = branches;
		this.chickenTypes = chickenTypes;
		this.dailyPrices = dailyPrices;
		this.mapper = mapper;
		this.documentNumbers = documentNumbers;
		this.branchAccess = branchAccess;
		this.receipts = receipts;
		this.settlements = settlements;
	}

	@Transactional
	@Audited(action = "BRANCH_SALE_RECORD", entityType = "BranchSale")
	public BranchSaleResponse record(BranchSaleCreateRequest request) {
		branchAccess.require(request.branchId());
		Branch branch = branches.lockById(request.branchId())
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
		Instant effectiveAt = BusinessTime.endOf(request.saleDate());

		BigDecimal totalSales = BigDecimal.ZERO;
		BigDecimal transferValue = BigDecimal.ZERO;

		for (var itemRequest : request.items()) {
			ChickenType chickenType = chickenTypes.findById(itemRequest.chickenTypeId())
					.orElseThrow(() -> new ResourceNotFoundException("ChickenType", itemRequest.chickenTypeId()));
			BigDecimal weight = itemRequest.weightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);

			BranchStock chickenStock = stock.computeIfAbsent(chickenType.getId(), id -> new BranchStock());
			BigDecimal available = chickenStock.availableWeight();
			if (weight.compareTo(available) > 0) {
				throw new BusinessException("INSUFFICIENT_BRANCH_STOCK",
						"Branch has " + available.stripTrailingZeros().toPlainString()
								+ " kg of " + chickenType.getName() + " available to sell",
						HttpStatus.CONFLICT);
			}

			BigDecimal sellingPrice = resolveSellingPrice(chickenType.getId(), branch.getId(), effectiveAt);
			// Cost the sold weight against the stock the branch still holds, oldest receipt first.
			BigDecimal cost = chickenStock.take(weight).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
			BigDecimal transferPrice = cost.divide(weight, COST_SCALE, RoundingMode.HALF_UP);

			BranchSaleItem item = new BranchSaleItem();
			item.setBranchSale(sale);
			item.setChickenType(chickenType);
			item.setWeightKg(weight);
			item.setSellingPricePerKg(sellingPrice);
			item.setTransferPricePerKg(transferPrice);
			item.setRevenue(weight.multiply(sellingPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
			item.setCost(cost);
			item.setProfit(item.getRevenue().subtract(item.getCost()));
			sale.getItems().add(item);

			totalSales = totalSales.add(item.getRevenue());
			transferValue = transferValue.add(item.getCost());
		}

		sale.setTotalSales(totalSales.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		sale.setTransferValue(transferValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		sale.setBranchProfit(totalSales.subtract(transferValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP));

		if (sale.getId() != null) {
			BigDecimal settled = settlements.settledAmount(sale.getId());
			if (sale.getTransferValue().compareTo(settled) < 0) {
				throw new BusinessException("BUSINESS_RULE_VIOLATION",
						"This day's sales already have " + settled.toPlainString()
								+ " settled; the new transfer value of " + sale.getTransferValue().toPlainString()
								+ " is lower. Reverse the settlement first.",
						HttpStatus.CONFLICT);
			}
		}

		return mapper.toResponse(sales.save(sale));
	}

	/**
	 * Weight the branch still holds per chicken type: received from distributions minus weight
	 * already sold.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, BigDecimal> availableWeightByChickenType(UUID branchId) {
		Branch branch = branches.findById(branchId)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		Map<UUID, BigDecimal> available = new HashMap<>();
		branchStock(branch, null).forEach((chickenTypeId, stock) -> available.put(chickenTypeId, stock.availableWeight()));
		return available;
	}

	/** Stock on hand at a branch per chicken type, for branch users and the owner. */
	@Transactional(readOnly = true)
	public List<BranchStockResponse> stock(UUID branchId) {
		branchAccess.require(branchId);
		return availableWeightByChickenType(branchId).entrySet().stream()
				.map(e -> new BranchStockResponse(e.getKey(), e.getValue()))
				.sorted(java.util.Comparator.comparing(r -> r.chickenTypeId().toString()))
				.toList();
	}

	@Transactional(readOnly = true)
	public Page<BranchSaleResponse> list(Pageable pageable, UUID branchId, LocalDate from, LocalDate to) {
		if (branchId != null) {
			branchAccess.require(branchId);
		}
		Specification<BranchSale> specification = branchAccess.restrict("branch");
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
		branchAccess.require(sale.getBranch().getId());
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

	/**
	 * Stock the branch holds per chicken type: its received lines as cost layers, oldest first,
	 * with the weight already sold (by sales other than {@code excludedSaleId}) consumed from the front.
	 */
	private Map<UUID, BranchStock> branchStock(Branch branch, UUID excludedSaleId) {
		Map<UUID, BranchStock> stock = new HashMap<>();
		for (ReceivedLine line : receipts.receivedLines(branch.getId(), RECEIVED_STATUSES)) {
			stock.computeIfAbsent(line.chickenTypeId(), id -> new BranchStock()).layers.add(line);
		}
		UUID excluded = excludedSaleId != null ? excludedSaleId : UUID.randomUUID();
		for (Object[] row : sales.soldWeightByChickenType(branch.getId(), excluded)) {
			BranchStock chickenStock = stock.computeIfAbsent((UUID) row[0], id -> new BranchStock());
			chickenStock.take((BigDecimal) row[1]);
		}
		return stock;
	}

	private static final class BranchStock {

		final List<ReceivedLine> layers = new ArrayList<>();
		private int layer;
		private BigDecimal usedInLayer = BigDecimal.ZERO;
		private BigDecimal consumed = BigDecimal.ZERO;

		BigDecimal availableWeight() {
			BigDecimal received = layers.stream().map(ReceivedLine::weightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
			return received.subtract(consumed).max(BigDecimal.ZERO).setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
		}

		/** Consumes weight from the oldest layers and returns its transfer cost (unrounded). */
		BigDecimal take(BigDecimal weight) {
			BigDecimal remaining = weight;
			BigDecimal cost = BigDecimal.ZERO;
			while (remaining.signum() > 0 && layer < layers.size()) {
				ReceivedLine current = layers.get(layer);
				BigDecimal left = current.weightKg().subtract(usedInLayer);
				BigDecimal used = left.min(remaining);
				cost = cost.add(used.multiply(current.transferPricePerKg()));
				usedInLayer = usedInLayer.add(used);
				remaining = remaining.subtract(used);
				if (usedInLayer.compareTo(current.weightKg()) >= 0) {
					layer++;
					usedInLayer = BigDecimal.ZERO;
				}
			}
			consumed = consumed.add(weight.subtract(remaining));
			return cost;
		}
	}
}
