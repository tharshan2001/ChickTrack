package com.chick.app.expense.service;

import com.chick.app.expense.domain.ExpenseCategory;
import com.chick.app.expense.dto.ExpenseCategoryCreateRequest;
import com.chick.app.expense.dto.ExpenseCategoryResponse;
import com.chick.app.expense.dto.ExpenseCategoryStatusRequest;
import com.chick.app.expense.dto.ExpenseCategoryUpdateRequest;
import com.chick.app.expense.mapper.ExpenseCategoryMapper;
import com.chick.app.expense.repository.ExpenseCategoryRepository;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseCategoryService {

	private final ExpenseCategoryRepository expenseCategories;
	private final ExpenseCategoryMapper mapper;

	public ExpenseCategoryService(ExpenseCategoryRepository expenseCategories, ExpenseCategoryMapper mapper) {
		this.expenseCategories = expenseCategories;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public Page<ExpenseCategoryResponse> list(Pageable pageable, Boolean active, String search) {
		Specification<ExpenseCategory> specification = (root, query, cb) -> cb.conjunction();
		if (active != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, cb) ->
					cb.like(cb.lower(root.get("name")), pattern));
		}
		return expenseCategories.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public ExpenseCategoryResponse get(UUID id) {
		return mapper.toResponse(expenseCategories.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id)));
	}

	@Transactional
	public ExpenseCategoryResponse create(ExpenseCategoryCreateRequest request) {
		return mapper.toResponse(expenseCategories.save(mapper.toEntity(request)));
	}

	@Transactional
	public ExpenseCategoryResponse update(UUID id, ExpenseCategoryUpdateRequest request) {
		ExpenseCategory expenseCategory = expenseCategories.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id));
		mapper.updateEntity(request, expenseCategory);
		return mapper.toResponse(expenseCategories.save(expenseCategory));
	}

	@Transactional
	public ExpenseCategoryResponse setStatus(UUID id, ExpenseCategoryStatusRequest request) {
		ExpenseCategory expenseCategory = expenseCategories.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id));
		expenseCategory.setActive(request.active());
		return mapper.toResponse(expenseCategories.save(expenseCategory));
	}
}
