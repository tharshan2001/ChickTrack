package com.chick.app.supplier.service;

import com.chick.app.audit.Audited;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.supplier.domain.Supplier;
import com.chick.app.supplier.dto.SupplierCreateRequest;
import com.chick.app.supplier.dto.SupplierResponse;
import com.chick.app.supplier.dto.SupplierStatusRequest;
import com.chick.app.supplier.dto.SupplierUpdateRequest;
import com.chick.app.supplier.mapper.SupplierMapper;
import com.chick.app.supplier.repository.SupplierRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

	private final SupplierRepository suppliers;
	private final SupplierMapper mapper;

	public SupplierService(SupplierRepository suppliers, SupplierMapper mapper) {
		this.suppliers = suppliers;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public Page<SupplierResponse> list(Pageable pageable, Boolean active, String search) {
		Specification<Supplier> specification = (root, query, cb) -> cb.conjunction();
		if (active != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, cb) -> cb.or(
					cb.like(cb.lower(root.get("name")), pattern),
					cb.like(cb.lower(root.get("contactPerson")), pattern),
					cb.like(cb.lower(root.get("phone")), pattern)));
		}
		return suppliers.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public SupplierResponse get(UUID id) {
		return mapper.toResponse(suppliers.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Supplier", id)));
	}

	@Transactional
	@Audited(action = "SUPPLIER_CREATE", entityType = "Supplier")
	public SupplierResponse create(SupplierCreateRequest request) {
		Supplier supplier = mapper.toEntity(request);
		return mapper.toResponse(suppliers.save(supplier));
	}

	@Transactional
	@Audited(action = "SUPPLIER_UPDATE", entityType = "Supplier")
	public SupplierResponse update(UUID id, SupplierUpdateRequest request) {
		Supplier supplier = suppliers.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
		mapper.updateEntity(request, supplier);
		return mapper.toResponse(suppliers.save(supplier));
	}

	@Transactional
	@Audited(action = "SUPPLIER_STATUS", entityType = "Supplier")
	public SupplierResponse setStatus(UUID id, SupplierStatusRequest request) {
		Supplier supplier = suppliers.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
		supplier.setActive(request.active());
		return mapper.toResponse(suppliers.save(supplier));
	}
}
