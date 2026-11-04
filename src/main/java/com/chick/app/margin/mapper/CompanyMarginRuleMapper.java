package com.chick.app.margin.mapper;

import com.chick.app.margin.domain.CompanyMarginRule;
import com.chick.app.margin.dto.CompanyMarginRuleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CompanyMarginRuleMapper {

	@Mapping(target = "chickenTypeId", source = "chickenType.id")
	@Mapping(target = "branchId", source = "branch.id")
	CompanyMarginRuleResponse toResponse(CompanyMarginRule rule);
}
