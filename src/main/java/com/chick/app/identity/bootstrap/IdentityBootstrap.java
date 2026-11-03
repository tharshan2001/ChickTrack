package com.chick.app.identity.bootstrap;

import com.chick.app.identity.domain.Role;
import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.RoleRepository;
import com.chick.app.identity.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(IdentityBootstrap.class);

	private final UserAccountRepository users;
	private final RoleRepository roles;
	private final PasswordEncoder passwordEncoder;
	private final String adminUsername;
	private final String adminPassword;

	public IdentityBootstrap(
			UserAccountRepository users,
			RoleRepository roles,
			PasswordEncoder passwordEncoder,
			@Value("${app.oauth.admin.username:admin}") String adminUsername,
			@Value("${app.oauth.admin.password:change-me}") String adminPassword) {
		this.users = users;
		this.roles = roles;
		this.passwordEncoder = passwordEncoder;
		this.adminUsername = adminUsername;
		this.adminPassword = adminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.count() > 0) {
			return;
		}

		Role owner = roles.findByName("OWNER")
				.orElseThrow(() -> new IllegalStateException("OWNER role is missing; check Flyway seed data"));

		UserAccount admin = new UserAccount();
		admin.setUsername(adminUsername);
		admin.setPasswordHash(passwordEncoder.encode(adminPassword));
		admin.setFullName("System Administrator");
		admin.setActive(true);
		admin.getRoles().add(owner);
		users.save(admin);

		if ("change-me".equals(adminPassword)) {
			log.warn("Bootstrap admin '{}' was created with the default password. Change it before deployment.",
					adminUsername);
		}
		log.info("Created bootstrap admin user '{}'", adminUsername);
	}
}
