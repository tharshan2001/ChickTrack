package com.chick.app.payment.mapper;

import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.domain.BranchSettlementAllocation;
import com.chick.app.payment.domain.Payment;
import com.chick.app.payment.domain.PaymentAllocation;
import com.chick.app.payment.dto.BranchSettlementAllocationRequest;
import com.chick.app.payment.dto.BranchSettlementAllocationResponse;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BranchSettlementResponse;
import com.chick.app.payment.dto.PaymentAllocationRequest;
import com.chick.app.payment.dto.PaymentAllocationResponse;
import com.chick.app.payment.dto.PaymentCreateRequest;
import com.chick.app.payment.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "supplier", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "reversalReason", ignore = true)
	@Mapping(target = "allocations", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Payment toEntity(PaymentCreateRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "payment", ignore = true)
	@Mapping(target = "procurement", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	PaymentAllocation toAllocation(PaymentAllocationRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "branch", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "reversalReason", ignore = true)
	@Mapping(target = "allocations", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	BranchSettlement toEntity(BranchSettlementCreateRequest request);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "branchSettlement", ignore = true)
	@Mapping(target = "distribution", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	BranchSettlementAllocation toAllocation(BranchSettlementAllocationRequest request);

	@Mapping(target = "supplierId", source = "supplier.id")
	@Mapping(target = "status", expression = "java(payment.getStatus().name())")
	PaymentResponse toResponse(Payment payment);

	@Mapping(target = "procurementId", source = "procurement.id")
	PaymentAllocationResponse toAllocationResponse(PaymentAllocation allocation);

	@Mapping(target = "branchId", source = "branch.id")
	@Mapping(target = "status", expression = "java(settlement.getStatus().name())")
	BranchSettlementResponse toResponse(BranchSettlement settlement);

	@Mapping(target = "distributionId", source = "distribution.id")
	BranchSettlementAllocationResponse toAllocationResponse(BranchSettlementAllocation allocation);
}
