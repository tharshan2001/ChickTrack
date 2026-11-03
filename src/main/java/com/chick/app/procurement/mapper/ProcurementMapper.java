package com.chick.app.procurement.mapper;

import com.chick.app.procurement.domain.Procurement;
import com.chick.app.procurement.domain.ProcurementExpense;
import com.chick.app.procurement.domain.ProcurementItem;
import com.chick.app.procurement.domain.ProcurementReceipt;
import com.chick.app.procurement.domain.ProcurementReceiptItem;
import com.chick.app.procurement.dto.ProcurementCreateRequest;
import com.chick.app.procurement.dto.ProcurementExpenseRequest;
import com.chick.app.procurement.dto.ProcurementExpenseResponse;
import com.chick.app.procurement.dto.ProcurementItemRequest;
import com.chick.app.procurement.dto.ProcurementItemResponse;
import com.chick.app.procurement.dto.ProcurementReceiptItemRequest;
import com.chick.app.procurement.dto.ProcurementReceiptItemResponse;
import com.chick.app.procurement.dto.ProcurementReceiptRequest;
import com.chick.app.procurement.dto.ProcurementReceiptResponse;
import com.chick.app.procurement.dto.ProcurementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProcurementMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "reference", ignore = true)
	@Mapping(target = "supplier", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "purchaseValue", ignore = true)
	@Mapping(target = "commissionAmount", ignore = true)
	@Mapping(target = "expenseAmount", ignore = true)
	@Mapping(target = "totalCost", ignore = true)
	@Mapping(target = "paidWeightKg", ignore = true)
	@Mapping(target = "receivedWeightKg", ignore = true)
	@Mapping(target = "landedCostPerKg", ignore = true)
	@Mapping(target = "reversalReason", ignore = true)
	@Mapping(target = "items", ignore = true)
	@Mapping(target = "expenses", ignore = true)
	@Mapping(target = "receipts", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Procurement toEntity(ProcurementCreateRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "procurement", ignore = true)
	@Mapping(target = "chickenType", ignore = true)
	@Mapping(target = "purchaseValue", ignore = true)
	@Mapping(target = "commissionAmount", ignore = true)
	@Mapping(target = "allocatedExpenseAmount", ignore = true)
	@Mapping(target = "totalCost", ignore = true)
	@Mapping(target = "receivedWeightKg", ignore = true)
	@Mapping(target = "landedCostPerKg", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	ProcurementItem toItem(ProcurementItemRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "procurement", ignore = true)
	@Mapping(target = "category", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	ProcurementExpense toExpense(ProcurementExpenseRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "procurement", ignore = true)
	@Mapping(target = "items", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	ProcurementReceipt toReceipt(ProcurementReceiptRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "receipt", ignore = true)
	@Mapping(target = "procurementItem", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	ProcurementReceiptItem toReceiptItem(ProcurementReceiptItemRequest request);

	@Mapping(target = "supplierId", source = "supplier.id")
	@Mapping(target = "status", expression = "java(procurement.getStatus().name())")
	ProcurementResponse toResponse(Procurement procurement);

	@Mapping(target = "chickenTypeId", source = "chickenType.id")
	@Mapping(target = "commissionMethod", expression = "java(item.getCommissionMethod().name())")
	ProcurementItemResponse toItemResponse(ProcurementItem item);

	@Mapping(target = "categoryId", source = "category.id")
	ProcurementExpenseResponse toExpenseResponse(ProcurementExpense expense);

	@Mapping(target = "procurementItemId", source = "procurementItem.id")
	ProcurementReceiptItemResponse toReceiptItemResponse(ProcurementReceiptItem item);

	@Mapping(target = "procurementId", source = "procurement.id")
	ProcurementReceiptResponse toReceiptResponse(ProcurementReceipt receipt);
}
