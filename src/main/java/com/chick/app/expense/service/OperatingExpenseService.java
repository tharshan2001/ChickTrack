package com.chick.app.expense.service;

import com.chick.app.audit.Audited;
import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.common.service.DocumentNumberService;
import com.chick.app.common.time.BusinessTime;
import com.chick.app.expense.domain.ExpenseCategory;
import com.chick.app.expense.domain.OperatingExpense;
import com.chick.app.expense.dto.OperatingExpenseRequest;
import com.chick.app.expense.dto.OperatingExpenseResponse;
import com.chick.app.expense.dto.OperatingExpenseVoidRequest;
import com.chick.app.expense.repository.ExpenseCategoryRepository;
import com.chick.app.expense.repository.OperatingExpenseRepository;
import com.chick.app.identity.security.BranchAccessService;
import com.chick.app.identity.security.BranchAccessService.BranchAccess;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operating expenses: running costs that are not part of a procurement or a distribution. They
 * reduce net profit in the profit and loss report. Expenses are voided, never deleted.
 */
@Service
public class OperatingExpenseService {

	private final OperatingExpenseRepository expenses;
	private final ExpenseCategoryRepository categories;
	private final BranchRepository branches;
	private final BranchAccessService branchAccess;
	private final DocumentNumberService documentNumbers;

	public OperatingExpenseService(
			OperatingExpenseRepository expenses,
			ExpenseCategoryRepository categories,
			BranchRepository branches,
			BranchAccessService branchAccess,
			DocumentNumberService documentNumbers) {
		this.expenses = expenses;
		this.categories = categories;
		this.branches = branches;
		this.branchAccess = branchAccess;
		this.documentNumbers = documentNumbers;
	}

	/**
	 * Lists expenses. {@code headOffice=true} returns only head-office expenses; {@code branchId}
	 * only that branch's. Head-office expenses are visible only to users who see every branch.
	 */
	@Transactional(readOnly = true)
	public Page<OperatingExpenseResponse> list(
			Pageable pageable,
			LocalDate from,
			LocalDate to,
			UUID categoryId,
			UUID branchId,
			Boolean headOffice,
			String status) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException("VALIDATION_FAILED", "'from' must not be after 'to'", HttpStatus.BAD_REQUEST);
		}
		BranchAccess access = branchAccess.current();
		Specification<OperatingExpense> spec = (root, query, cb) -> cb.conjunction();
		if (from != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
		}
		if (to != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), to));
		}
		if (categoryId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
		}
		if (status != null && !status.isBlank()) {
			OperatingExpense.Status parsed = parseStatus(status);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
		}
		if (branchId != null) {
			branchAccess.require(branchId);
			spec = spec.and((root, query, cb) -> cb.equal(root.get("branch").get("id"), branchId));
		}
		else if (Boolean.TRUE.equals(headOffice)) {
			if (!access.all()) {
				throw BranchAccessService.forbidden();
			}
			spec = spec.and((root, query, cb) -> cb.isNull(root.get("branch")));
		}
		else if (!access.all()) {
			// Branch-scoped users see their branches' expenses and never head office's.
			spec = spec.and(access.branchIds().isEmpty()
					? (root, query, cb) -> cb.disjunction()
					: (root, query, cb) -> root.get("branch").get("id").in(access.branchIds()));
		}
		return expenses.findAll(spec, pageable).map(OperatingExpenseService::toResponse);
	}

	@Transactional(readOnly = true)
	public OperatingExpenseResponse get(UUID id) {
		return toResponse(findVisible(id));
	}

	@Transactional
	@Audited(action = "OPERATING_EXPENSE_CREATE", entityType = "OperatingExpense")
	public OperatingExpenseResponse create(OperatingExpenseRequest request) {
		OperatingExpense expense = new OperatingExpense();
		apply(expense, request, true);
		expense.setReference(documentNumbers.next("OPERATING_EXPENSE", "EXP"));
		return toResponse(expenses.save(expense));
	}

	@Transactional
	@Audited(action = "OPERATING_EXPENSE_UPDATE", entityType = "OperatingExpense")
	public OperatingExpenseResponse update(UUID id, OperatingExpenseRequest request) {
		OperatingExpense expense = findVisible(id);
		ensurePosted(expense);
		boolean categoryChanged = !expense.getCategory().getId().equals(request.categoryId());
		apply(expense, request, categoryChanged);
		return toResponse(expenses.save(expense));
	}

	@Transactional
	@Audited(action = "OPERATING_EXPENSE_VOID", entityType = "OperatingExpense")
	public OperatingExpenseResponse voidExpense(UUID id, OperatingExpenseVoidRequest request) {
		OperatingExpense expense = findVisible(id);
		ensurePosted(expense);
		expense.setStatus(OperatingExpense.Status.VOIDED);
		expense.setVoidReason(request.reason().trim());
		return toResponse(expenses.save(expense));
	}

	private void apply(OperatingExpense expense, OperatingExpenseRequest request, boolean requireActiveCategory) {
		if (request.expenseDate().isAfter(BusinessTime.today())) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Expense date cannot be in the future", HttpStatus.BAD_REQUEST);
		}
		ExpenseCategory category = categories.findById(request.categoryId())
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", request.categoryId()));
		if (requireActiveCategory && !category.isActive()) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Expense category " + category.getName() + " is inactive", HttpStatus.BAD_REQUEST);
		}
		Branch branch = null;
		if (request.branchId() != null) {
			branchAccess.require(request.branchId());
			branch = branches.findById(request.branchId())
					.orElseThrow(() -> new ResourceNotFoundException("Branch", request.branchId()));
		}
		else if (!branchAccess.current().all()) {
			throw BranchAccessService.forbidden();
		}
		expense.setExpenseDate(request.expenseDate());
		expense.setCategory(category);
		expense.setBranch(branch);
		expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
		expense.setPayee(blankToNull(request.payee()));
		expense.setDescription(blankToNull(request.description()));
	}

	private OperatingExpense findVisible(UUID id) {
		OperatingExpense expense = expenses.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("OperatingExpense", id));
		UUID branchId = expense.getBranch() == null ? null : expense.getBranch().getId();
		BranchAccess access = branchAccess.current();
		if (branchId == null ? !access.all() : !access.allows(branchId)) {
			throw BranchAccessService.forbidden();
		}
		return expense;
	}

	private static void ensurePosted(OperatingExpense expense) {
		if (expense.getStatus() != OperatingExpense.Status.POSTED) {
			throw new BusinessException("BUSINESS_RULE_VIOLATION",
					"Expense " + expense.getReference() + " is voided", HttpStatus.CONFLICT);
		}
	}

	private static OperatingExpense.Status parseStatus(String status) {
		try {
			return OperatingExpense.Status.valueOf(status.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException("VALIDATION_FAILED",
					"status must be POSTED or VOIDED", HttpStatus.BAD_REQUEST);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static OperatingExpenseResponse toResponse(OperatingExpense expense) {
		Branch branch = expense.getBranch();
		return new OperatingExpenseResponse(
				expense.getId(),
				expense.getReference(),
				expense.getExpenseDate(),
				expense.getCategory().getId(),
				expense.getCategory().getName(),
				branch == null ? null : branch.getId(),
				branch == null ? null : branch.getName(),
				expense.getAmount(),
				expense.getPayee(),
				expense.getDescription(),
				expense.getStatus().name(),
				expense.getVoidReason(),
				expense.getVersion(),
				expense.getCreatedAt(),
				expense.getUpdatedAt());
	}
}
