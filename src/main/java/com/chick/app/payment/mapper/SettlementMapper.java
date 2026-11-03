package com.chick.app.payment.mapper;

import com.chick.app.payment.domain.BranchSettlement;
import com.chick.app.payment.domain.BranchSettlementAllocation;
import com.chick.app.payment.dto.BranchSettlementAllocationRequest;
import com.chick.app.payment.dto.BranchSettlementAllocationResponse;
import com.chick.app.payment.dto.BranchSettlementCreateRequest;
import com.chick.app.payment.dto.BranchSettlementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SettlementMapper {

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
	@Mapping(target = "branchSale", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	BranchSettlementAllocation toAllocation(BranchSettlementAllocationRequest request);

	@Mapping(target = "branchId", source = "branch.id")
	@Mapping(target = "status", expression = "java(settlement.getStatus().name())")
	BranchSettlementResponse toResponse(BranchSettlement settlement);

	@Mapping(target = "branchSaleId", source = "branchSale.id")
	BranchSettlementAllocationResponse toAllocationResponse(BranchSettlementAllocation allocation);
}
