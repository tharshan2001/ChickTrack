package com.chick.app.branch.service;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.dto.BranchCreateRequest;
import com.chick.app.branch.dto.BranchResponse;
import com.chick.app.branch.dto.BranchStatusRequest;
import com.chick.app.branch.dto.BranchUpdateRequest;
import com.chick.app.branch.mapper.BranchMapper;
import com.chick.app.branch.repository.BranchRepository;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.common.exception.ResourceNotFoundException;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.security.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

	private final BranchRepository branches;
	private final BranchMapper mapper;
	private final CurrentUserService currentUserService;

	public BranchService(BranchRepository branches, BranchMapper mapper, CurrentUserService currentUserService) {
		this.branches = branches;
		this.mapper = mapper;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public Page<BranchResponse> list(Pageable pageable, Boolean active, String search) {
		UserAccount user = currentUserService.currentUser();
		Specification<Branch> specification = (root, query, cb) -> cb.conjunction();
		if (active != null) {
			specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.trim().toLowerCase() + "%";
			specification = specification.and((root, query, cb) -> cb.or(
					cb.like(cb.lower(root.get("code")), pattern),
					cb.like(cb.lower(root.get("name")), pattern)));
		}
		if (!currentUserService.isOwner(user)) {
			List<UUID> allowed = user.getBranches().stream().map(b -> b.getId()).toList();
			if (allowed.isEmpty()) {
				return Page.empty(pageable);
			}
			specification = specification.and((root, query, cb) -> root.get("id").in(allowed));
		}
		return branches.findAll(specification, pageable).map(mapper::toResponse);
	}

	@Transactional(readOnly = true)
	public BranchResponse get(UUID id) {
		Branch branch = branches.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", id));
		checkAccess(branch);
		return mapper.toResponse(branch);
	}

	@Transactional
	public BranchResponse create(BranchCreateRequest request) {
		if (branches.existsByCode(request.code())) {
			throw new BusinessException("CONFLICT", "Branch code already exists: " + request.code(), HttpStatus.CONFLICT);
		}
		Branch branch = mapper.toEntity(request);
		return mapper.toResponse(branches.save(branch));
	}

	@Transactional
	public BranchResponse update(UUID id, BranchUpdateRequest request) {
		Branch branch = branches.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", id));
		mapper.updateEntity(request, branch);
		return mapper.toResponse(branches.save(branch));
	}

	@Transactional
	public BranchResponse setStatus(UUID id, BranchStatusRequest request) {
		Branch branch = branches.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Branch", id));
		branch.setActive(request.active());
		return mapper.toResponse(branches.save(branch));
	}

	private void checkAccess(Branch branch) {
		UserAccount user = currentUserService.currentUser();
		if (currentUserService.isOwner(user)) {
			return;
		}
		boolean allowed = user.getBranches().stream().anyMatch(b -> b.getId().equals(branch.getId()));
		if (!allowed) {
			throw new BusinessException("FORBIDDEN", "Branch access denied", HttpStatus.FORBIDDEN);
		}
	}
}
