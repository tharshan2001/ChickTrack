package com.chick.app.chicken.mapper;

import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.dto.ChickenTypeCreateRequest;
import com.chick.app.chicken.dto.ChickenTypeResponse;
import com.chick.app.chicken.dto.ChickenTypeUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ChickenTypeMapper {

	ChickenType toEntity(ChickenTypeCreateRequest request);

	void updateEntity(ChickenTypeUpdateRequest request, @MappingTarget ChickenType chickenType);

	ChickenTypeResponse toResponse(ChickenType chickenType);
}
