package com.chick.app.expense.mapper;

import com.chick.app.expense.domain.ExpenseCategory;
import com.chick.app.expense.dto.ExpenseCategoryCreateRequest;
import com.chick.app.expense.dto.ExpenseCategoryResponse;
import com.chick.app.expense.dto.ExpenseCategoryUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ExpenseCategoryMapper {

	ExpenseCategory toEntity(ExpenseCategoryCreateRequest request);

	void updateEntity(ExpenseCategoryUpdateRequest request, @MappingTarget ExpenseCategory expenseCategory);

	ExpenseCategoryResponse toResponse(ExpenseCategory expenseCategory);
}
