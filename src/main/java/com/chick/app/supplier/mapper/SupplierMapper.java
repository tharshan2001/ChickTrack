package com.chick.app.supplier.mapper;

import com.chick.app.supplier.domain.Supplier;
import com.chick.app.supplier.dto.SupplierCreateRequest;
import com.chick.app.supplier.dto.SupplierResponse;
import com.chick.app.supplier.dto.SupplierUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

	Supplier toEntity(SupplierCreateRequest request);

	void updateEntity(SupplierUpdateRequest request, @MappingTarget Supplier supplier);

	SupplierResponse toResponse(Supplier supplier);
}
