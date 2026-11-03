package com.chick.app.distribution.mapper;

import com.chick.app.distribution.domain.BranchReceipt;
import com.chick.app.distribution.domain.BranchReceiptItem;
import com.chick.app.distribution.domain.Distribution;
import com.chick.app.distribution.domain.DistributionExpense;
import com.chick.app.distribution.domain.DistributionItem;
import com.chick.app.distribution.dto.BranchReceiptItemResponse;
import com.chick.app.distribution.dto.BranchReceiptResponse;
import com.chick.app.distribution.dto.DistributionCreateRequest;
import com.chick.app.distribution.dto.DistributionItemRequest;
import com.chick.app.distribution.dto.DistributionExpenseRequest;
import com.chick.app.distribution.dto.DistributionExpenseResponse;
import com.chick.app.distribution.dto.DistributionItemResponse;
import com.chick.app.distribution.dto.DistributionResponse;
import com.chick.app.distribution.dto.BranchReceiptResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DistributionMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "reference", ignore = true)
	@Mapping(target = "branch", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "totalWeightKg", ignore = true)
	@Mapping(target = "totalCost", ignore = true)
	@Mapping(target = "totalRevenue", ignore = true)
	@Mapping(target = "totalMargin", ignore = true)
	@Mapping(target = "expenseAmount", ignore = true)
	@Mapping(target = "items", ignore = true)
	@Mapping(target = "expenses", ignore = true)
	@Mapping(target = "receipts", ignore = true)
	@Mapping(target = "reversalReason", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Distribution toEntity(DistributionCreateRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "distribution", ignore = true)
	@Mapping(target = "stockBatch", ignore = true)
	@Mapping(target = "chickenType", ignore = true)
	@Mapping(target = "lineCost", ignore = true)
	@Mapping(target = "lineRevenue", ignore = true)
	@Mapping(target = "lineMargin", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	DistributionItem toItem(DistributionItemRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "distribution", ignore = true)
	@Mapping(target = "category", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	DistributionExpense toExpense(DistributionExpenseRequest request);

	@Mapping(target = "stockBatchId", source = "stockBatch.id")
	@Mapping(target = "chickenTypeId", source = "chickenType.id")
	DistributionItemResponse toItemResponse(DistributionItem item);

	@Mapping(target = "categoryId", source = "category.id")
	DistributionExpenseResponse toExpenseResponse(DistributionExpense expense);

	@Mapping(target = "distributionId", source = "distribution.id")
	BranchReceiptResponse toReceiptResponse(BranchReceipt receipt);

	@Mapping(target = "distributionItemId", source = "distributionItem.id")
	BranchReceiptItemResponse toReceiptItemResponse(BranchReceiptItem item);

	@Mapping(target = "branchId", source = "branch.id")
	@Mapping(target = "status", expression = "java(distribution.getStatus().name())")
	DistributionResponse toResponse(Distribution distribution);
}
