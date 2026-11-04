package com.chick.app.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chick.app.branch.domain.Branch;
import com.chick.app.common.exception.BusinessException;
import com.chick.app.identity.domain.Role;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.UserAccountRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class BranchAccessServiceTest {

	private final UserAccountRepository users = mock(UserAccountRepository.class);
	private final BranchAccessService service = new BranchAccessService(users);
	private final UUID ownBranch = UUID.randomUUID();
	private final UUID otherBranch = UUID.randomUUID();

	@BeforeEach
	void authenticate() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("alice", "n/a", AuthorityUtils.NO_AUTHORITIES));
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void ownerSeesEveryBranch() {
		givenUser(Set.of("OWNER"), Set.of(ownBranch));
		assertThat(service.current().allows(otherBranch)).isTrue();
	}

	@Test
	void branchReceiverSeesOnlyAssignedBranches() {
		givenUser(Set.of("BRANCH_RECEIVER"), Set.of(ownBranch));
		service.require(ownBranch);
		assertThatThrownBy(() -> service.require(otherBranch))
				.isInstanceOf(BusinessException.class)
				.extracting("code").isEqualTo("FORBIDDEN");
	}

	@Test
	void branchUserWithoutAssignedBranchesSeesNothing() {
		givenUser(Set.of("BRANCH_RECEIVER"), Set.of());
		assertThat(service.current().all()).isFalse();
		assertThat(service.current().allows(ownBranch)).isFalse();
	}

	@Test
	void headOfficeRoleWithoutAssignedBranchesSeesEveryBranch() {
		givenUser(Set.of("ACCOUNTANT"), Set.of());
		assertThat(service.current().allows(otherBranch)).isTrue();
	}

	@Test
	void headOfficeRoleWithAssignedBranchesIsRestrictedToThem() {
		givenUser(Set.of("DISTRIBUTION_MANAGER"), Set.of(ownBranch));
		assertThat(service.current().allows(ownBranch)).isTrue();
		assertThat(service.current().allows(otherBranch)).isFalse();
	}

	@Test
	void unauthenticatedOrInactiveUserSeesNothing() {
		givenUser(Set.of("OWNER"), Set.of());
		users.findByUsername("alice").orElseThrow().setActive(false);
		assertThat(service.current().allows(ownBranch)).isFalse();

		SecurityContextHolder.clearContext();
		assertThat(service.current().allows(ownBranch)).isFalse();
	}

	private void givenUser(Set<String> roleNames, Set<UUID> branchIds) {
		UserAccount user = new UserAccount();
		user.setUsername("alice");
		for (String name : roleNames) {
			Role role = new Role();
			role.setName(name);
			user.getRoles().add(role);
		}
		for (UUID id : branchIds) {
			Branch branch = new Branch();
			branch.setId(id);
			user.getBranches().add(branch);
		}
		when(users.findByUsername("alice")).thenReturn(Optional.of(user));
	}
}
