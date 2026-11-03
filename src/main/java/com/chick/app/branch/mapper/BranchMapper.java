package com.chick.app.branch.mapper;

import com.chick.app.branch.domain.Branch;
import com.chick.app.branch.dto.BranchCreateRequest;
import com.chick.app.branch.dto.BranchResponse;
import com.chick.app.branch.dto.BranchUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BranchMapper {

	Branch toEntity(BranchCreateRequest request);

	void updateEntity(BranchUpdateRequest request, @MappingTarget Branch branch);

	BranchResponse toResponse(Branch branch);
}
