package com.chick.app.identity.mapper;

import com.chick.app.branch.domain.Branch;
import com.chick.app.identity.domain.Role;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.dto.RoleResponse;
import com.chick.app.identity.dto.UserResponse;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

	RoleResponse toResponse(Role role);

	default UserResponse toResponse(UserAccount user) {
		Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
		Set<UUID> branchIds = user.getBranches().stream().map(Branch::getId).collect(Collectors.toSet());
		return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getPhone(),
				user.isActive(), roles, branchIds, user.getVersion(), user.getCreatedAt(), user.getUpdatedAt());
	}
}
