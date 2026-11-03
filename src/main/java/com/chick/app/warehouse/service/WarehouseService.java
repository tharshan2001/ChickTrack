package com.chick.app.warehouse.service;

import com.chick.app.warehouse.domain.Warehouse;
import com.chick.app.warehouse.dto.WarehouseCreateRequest;
import com.chick.app.warehouse.dto.WarehouseResponse;
import com.chick.app.warehouse.dto.WarehouseStatusRequest;
import com.chick.app.warehouse.dto.WarehouseUpdateRequest;
import com.chick.app.warehouse.mapper.WarehouseMapper;
import com.chick.app.warehouse.repository.WarehouseRepository;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {

	private final WarehouseRepository warehouses;
	private final WarehouseMapper mapper;

	public WarehouseService(WarehouseRepository warehouses, WarehouseMapper mapper) {
		this.warehouses = warehouses;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public Page<WarehouseResponse> list(Pageable pageable, Boolean active, String search) {
		Specification<Warehouse> specification = (root, query, cb) -> cb.conjunction();
		if (active != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, cb) ->
					cb.like(cb.lower(root.get("name")), pattern));
		}
		return warehouses.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public WarehouseResponse get(UUID id) {
		return mapper.toResponse(warehouses.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Warehouse", id)));
	}

	@Transactional
	public WarehouseResponse create(WarehouseCreateRequest request) {
		return mapper.toResponse(warehouses.save(mapper.toEntity(request)));
	}

	@Transactional
	public WarehouseResponse update(UUID id, WarehouseUpdateRequest request) {
		Warehouse warehouse = warehouses.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
		mapper.updateEntity(request, warehouse);
		return mapper.toResponse(warehouses.save(warehouse));
	}

	@Transactional
	public WarehouseResponse setStatus(UUID id, WarehouseStatusRequest request) {
		Warehouse warehouse = warehouses.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
		warehouse.setActive(request.active());
		return mapper.toResponse(warehouses.save(warehouse));
	}
}
