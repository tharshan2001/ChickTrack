package com.chick.app.identity.security;

import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

	private final UserAccountRepository users;

	public CurrentUserService(UserAccountRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public UserAccount currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new IllegalStateException("No authenticated user");
		}
		return users.findByUsername(authentication.getName())
				.orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + authentication.getName()));
	}

	public boolean isOwner(UserAccount user) {
		return user.getRoles().stream().anyMatch(role -> "OWNER".equals(role.getName()));
	}
}
