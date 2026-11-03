package com.chick.app.pricing.mapper;

import com.chick.app.pricing.domain.DailyPrice;
import com.chick.app.pricing.dto.DailyPriceCreateRequest;
import com.chick.app.pricing.dto.DailyPriceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DailyPriceMapper {

	@Mapping(target = "chickenType", ignore = true)
	@Mapping(target = "branch", ignore = true)
	@Mapping(target = "supplier", ignore = true)
	DailyPrice toEntity(DailyPriceCreateRequest request);

	@Mapping(target = "chickenTypeId", source = "chickenType.id")
	@Mapping(target = "branchId", source = "branch.id")
	@Mapping(target = "supplierId", source = "supplier.id")
	DailyPriceResponse toResponse(DailyPrice dailyPrice);
}
