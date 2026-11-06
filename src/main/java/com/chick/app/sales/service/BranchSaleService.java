package com.chick.app.sales.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.common.time.BusinessTime;
import com.chick.app.distribution.domain.DistributionStatus;
import com.chick.app.distribution.repository.BranchReceiptRepository;
import com.chick.app.distribution.repository.ReceivedLine;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branch day close. The branch enters what it took in (total sales, Rs) and the stock it has left per
 * chicken type. The weight that left the branch is the stock before the day minus the closing stock,
 * costed oldest receipt first. What the branch owes is created on receipt, not here.
 */
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

	public BranchSaleService(
			BranchSaleRepository sales,
			BranchRepository branches,
			ChickenTypeRepository chickenTypes,
			DailyPriceService dailyPrices,
			BranchSaleMapper mapper,
			DocumentNumberService documentNumbers,
			BranchAccessService branchAccess,
			BranchReceiptRepository receipts) {
		this.sales = sales;
		this.branches = branches;
		this.chickenTypes = chickenTypes;
		this.dailyPrices = dailyPrices;
		this.mapper = mapper;
		this.documentNumbers = documentNumbers;
		this.branchAccess = branchAccess;
		this.receipts = receipts;
	}

	@Transactional
	@Audited(action = "BRANCH_SALE_RECORD", entityType = "BranchSale")
	public BranchSaleResponse record(BranchSaleCreateRequest request) {
		branchAccess.require(request.branchId());
		Branch branch = branches.lockById(request.branchId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));

		// Closing stock of a day is the opening stock of the next, so only the latest day can change.
		sales.findFirstByBranchIdAndSaleDateAfterOrderBySaleDateDesc(branch.getId(), request.saleDate())
				.ifPresent(later -> {
					throw new BusinessException("BUSINESS_RULE_VIOLATION",
							"The close of " + later.getSaleDate() + " is already saved; only the latest day can be changed",
							HttpStatus.CONFLICT);
				});

		Map<UUID, BigDecimal> closingByType = new LinkedHashMap<>();
		for (var itemRequest : request.items()) {
			if (closingByType.put(itemRequest.chickenTypeId(),
					itemRequest.closingWeightKg().setScale(WEIGHT_SCALE, RoundingMode.HALF_UP)) != null) {
				throw new BusinessException("VALIDATION_FAILED",
						"Enter one closing stock per chicken type", HttpStatus.BAD_REQUEST);
			}
		}

		Map<UUID, BranchStock> stock = stockBefore(branch.getId(), request.saleDate());
		for (var entry : stock.entrySet()) {
			if (entry.getValue().availableWeight().signum() > 0 && !closingByType.containsKey(entry.getKey())) {
				throw new BusinessException("VALIDATION_FAILED",
						"Enter the closing stock of " + chickenTypeName(entry.getKey()), HttpStatus.BAD_REQUEST);
			}
		}

		BranchSale sale = sales.findByBranchAndSaleDate(branch, request.saleDate()).orElseGet(BranchSale::new);
		if (sale.getId() == null) {
			sale.setReference(documentNumbers.next("BRANCH_SALE", "SALE"));
		}
		sale.setBranch(branch);
		sale.setSaleDate(request.saleDate());
		sale.setNotes(request.notes());
		sale.getItems().clear();

		Instant effectiveAt = BusinessTime.endOf(request.saleDate());
		BigDecimal cogs = BigDecimal.ZERO;
		BigDecimal expected = BigDecimal.ZERO;
		boolean expectedKnown = true;

		for (var entry : closingByType.entrySet()) {
			ChickenType chickenType = chickenTypes.findById(entry.getKey())
					.orElseThrow(() -> new ResourceNotFoundException("ChickenType", entry.getKey()));
			BranchStock chickenStock = stock.computeIfAbsent(chickenType.getId(), id -> new BranchStock());
			BigDecimal opening = chickenStock.availableWeight();
			BigDecimal closing = entry.getValue();
			if (closing.compareTo(opening) > 0) {
				throw new BusinessException("CLOSING_ABOVE_STOCK",
						"Closing stock of " + chickenType.getName() + " is more than the "
								+ opening.stripTrailingZeros().toPlainString() + " kg the branch held",
						HttpStatus.CONFLICT);
			}
			BigDecimal sold = opening.subtract(closing);
			BigDecimal cost = chickenStock.take(sold).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

			BranchSaleItem item = new BranchSaleItem();
			item.setBranchSale(sale);
			item.setChickenType(chickenType);
			item.setOpeningWeightKg(opening);
			item.setClosingWeightKg(closing);
			item.setWeightKg(sold);
			item.setCost(cost);
			item.setTransferPricePerKg(sold.signum() == 0
					? BigDecimal.ZERO.setScale(COST_SCALE)
					: cost.divide(sold, COST_SCALE, RoundingMode.HALF_UP));

			BigDecimal price = sellingPrice(chickenType.getId(), branch.getId(), effectiveAt);
			if (price != null) {
				item.setSellingPricePerKg(price);
				item.setRevenue(sold.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
				item.setProfit(item.getRevenue().subtract(cost));
				expected = expected.add(item.getRevenue());
			}
			else if (sold.signum() > 0) {
				expectedKnown = false;
			}
			sale.getItems().add(item);
			cogs = cogs.add(cost);
		}

		BigDecimal totalSales = request.totalSales().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
		sale.setTotalSales(totalSales);
		sale.setExpectedSales(expectedKnown ? expected.setScale(MONEY_SCALE, RoundingMode.HALF_UP) : null);
		sale.setTransferValue(cogs.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
		sale.setBranchProfit(totalSales.subtract(sale.getTransferValue()));
		return mapper.toResponse(sales.save(sale));
	}

	/** Weight the branch holds now per chicken type: everything received minus every closed day. */
	@Transactional(readOnly = true)
	public Map<UUID, BigDecimal> availableWeightByChickenType(UUID branchId) {
		branches.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		Map<UUID, BigDecimal> available = new HashMap<>();
		currentStock(branchId).forEach((chickenTypeId, s) -> available.put(chickenTypeId, s.availableWeight()));
		return available;
	}

	/**
	 * Stock on hand at a branch per chicken type. With a date, the stock before that day closes
	 * (its opening stock): received by the end of the day minus earlier closed days.
	 */
	@Transactional(readOnly = true)
	public List<BranchStockResponse> stock(UUID branchId, LocalDate date) {
		branchAccess.require(branchId);
		branches.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		Map<UUID, BranchStock> stock = date == null ? currentStock(branchId) : stockBefore(branchId, date);
		return stock.entrySet().stream()
				.map(e -> new BranchStockResponse(e.getKey(), e.getValue().availableWeight()))
				.sorted(Comparator.comparing(r -> r.chickenTypeId().toString()))
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

	/** Branch selling price for the day, or null when none is set (expected sales are then unknown). */
	private BigDecimal sellingPrice(UUID chickenTypeId, UUID branchId, Instant effectiveAt) {
		try {
			DailyPriceResponse price = dailyPrices.current(chickenTypeId, branchId, null, effectiveAt);
			return price.sellingPricePerKg();
		}
		catch (ResourceNotFoundException ex) {
			return null;
		}
	}

	private String chickenTypeName(UUID chickenTypeId) {
		return chickenTypes.findById(chickenTypeId).map(ChickenType::getName).orElse("every chicken type");
	}

	/** Received lines as cost layers, with every closed day's weight consumed from the front. */
	private Map<UUID, BranchStock> currentStock(UUID branchId) {
		Map<UUID, BranchStock> stock = layers(branchId, null);
		consume(stock, sales.soldWeight(branchId));
		return stock;
	}

	/** Stock before {@code date} closes: received by the end of that day, minus earlier closed days. */
	private Map<UUID, BranchStock> stockBefore(UUID branchId, LocalDate date) {
		Map<UUID, BranchStock> stock = layers(branchId, BusinessTime.endOf(date));
		consume(stock, sales.soldWeightBefore(branchId, date));
		return stock;
	}

	private Map<UUID, BranchStock> layers(UUID branchId, Instant receivedBy) {
		Map<UUID, BranchStock> stock = new HashMap<>();
		for (ReceivedLine line : receipts.receivedLines(branchId, RECEIVED_STATUSES)) {
			if (receivedBy == null || !line.receivedAt().isAfter(receivedBy)) {
				stock.computeIfAbsent(line.chickenTypeId(), id -> new BranchStock()).layers.add(line);
			}
		}
		return stock;
	}

	private static void consume(Map<UUID, BranchStock> stock, List<Object[]> soldRows) {
		for (Object[] row : soldRows) {
			stock.computeIfAbsent((UUID) row[0], id -> new BranchStock()).take((BigDecimal) row[1]);
		}
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
