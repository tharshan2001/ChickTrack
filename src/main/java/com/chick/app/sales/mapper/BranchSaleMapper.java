package com.chick.app.sales.mapper;

import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.dto.BranchSaleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BranchSaleMapper {

	@Mapping(target = "branchId", source = "branch.id")
	BranchSaleResponse toResponse(BranchSale sale);
}
