package com.chick.app.chicken.service;

import com.chick.app.audit.Audited;
import com.chick.app.chicken.domain.ChickenType;
import com.chick.app.chicken.dto.ChickenTypeCreateRequest;
import com.chick.app.chicken.dto.ChickenTypeResponse;
import com.chick.app.chicken.dto.ChickenTypeStatusRequest;
import com.chick.app.chicken.dto.ChickenTypeUpdateRequest;
import com.chick.app.chicken.mapper.ChickenTypeMapper;
import com.chick.app.chicken.repository.ChickenTypeRepository;
import com.chick.app.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChickenTypeService {

	private final ChickenTypeRepository chickenTypes;
	private final ChickenTypeMapper mapper;

	public ChickenTypeService(ChickenTypeRepository chickenTypes, ChickenTypeMapper mapper) {
		this.chickenTypes = chickenTypes;
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public Page<ChickenTypeResponse> list(Pageable pageable, Boolean active, String search) {
		Specification<ChickenType> specification = (root, query, cb) -> cb.conjunction();
		if (active != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, cb) ->
					cb.like(cb.lower(root.get("name")), pattern));
		}
		return chickenTypes.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public ChickenTypeResponse get(UUID id) {
		return mapper.toResponse(chickenTypes.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ChickenType", id)));
	}

	@Transactional
	@Audited(action = "CHICKEN_TYPE_CREATE", entityType = "ChickenType")
	public ChickenTypeResponse create(ChickenTypeCreateRequest request) {
		return mapper.toResponse(chickenTypes.save(mapper.toEntity(request)));
	}

	@Transactional
	@Audited(action = "CHICKEN_TYPE_UPDATE", entityType = "ChickenType")
	public ChickenTypeResponse update(UUID id, ChickenTypeUpdateRequest request) {
		ChickenType chickenType = chickenTypes.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ChickenType", id));
		mapper.updateEntity(request, chickenType);
		return mapper.toResponse(chickenTypes.save(chickenType));
	}

	@Transactional
	@Audited(action = "CHICKEN_TYPE_STATUS", entityType = "ChickenType")
	public ChickenTypeResponse setStatus(UUID id, ChickenTypeStatusRequest request) {
		ChickenType chickenType = chickenTypes.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ChickenType", id));
		chickenType.setActive(request.active());
		return mapper.toResponse(chickenTypes.save(chickenType));
	}
}
