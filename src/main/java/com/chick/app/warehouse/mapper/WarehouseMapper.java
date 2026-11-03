package com.chick.app.warehouse.mapper;

import com.chick.app.warehouse.domain.Warehouse;
import com.chick.app.warehouse.dto.WarehouseCreateRequest;
import com.chick.app.warehouse.dto.WarehouseResponse;
import com.chick.app.warehouse.dto.WarehouseUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface WarehouseMapper {

	Warehouse toEntity(WarehouseCreateRequest request);

	void updateEntity(WarehouseUpdateRequest request, @MappingTarget Warehouse warehouse);

	WarehouseResponse toResponse(Warehouse warehouse);
}
