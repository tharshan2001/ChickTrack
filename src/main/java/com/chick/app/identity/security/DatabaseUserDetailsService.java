package com.chick.app.identity.security;

import com.chick.app.identity.domain.Role;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserAccountRepository users;

	public DatabaseUserDetailsService(UserAccountRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UserAccount user = users.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

		String[] roles = user.getRoles().stream()
				.map(Role::getName)
				.toArray(String[]::new);

		return User.withUsername(user.getUsername())
				.password(user.getPasswordHash())
				.disabled(!user.isActive())
				.roles(roles)
				.build();
	}
}
