package com.chick.app.sales.mapper;

import com.chick.app.sales.domain.BranchSale;
import com.chick.app.sales.domain.BranchSaleItem;
import com.chick.app.sales.dto.BranchSaleItemResponse;
import com.chick.app.sales.dto.BranchSaleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BranchSaleMapper {

	@Mapping(target = "branchId", source = "branch.id")
	@Mapping(target = "variance", expression = "java(sale.getExpectedSales() == null ? null : sale.getTotalSales().subtract(sale.getExpectedSales()))")
	BranchSaleResponse toResponse(BranchSale sale);

	@Mapping(target = "chickenTypeId", source = "chickenType.id")
	BranchSaleItemResponse toItemResponse(BranchSaleItem item);
}
