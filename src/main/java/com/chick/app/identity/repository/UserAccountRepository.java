package com.chick.app.identity.repository;

import com.chick.app.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository
		extends JpaRepository<UserAccount, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UserAccount> {

	Optional<UserAccount> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByUsernameIgnoreCase(String username);

	@org.springframework.data.jpa.repository.Query(
			"select count(u) from UserAccount u join u.roles r where u.active = true and r.name = 'OWNER'")
	long countActiveOwners();
}
