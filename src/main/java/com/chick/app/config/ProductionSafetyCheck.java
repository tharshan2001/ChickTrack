package com.chick.app.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stops a production start with unsafe settings: default or weak passwords, an http issuer, or no
 * persistent signing key. Runs before the owner is seeded, so a weak password never reaches the
 * database.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck implements InitializingBean {

	private static final Set<String> KNOWN_DEFAULTS = Set.of("change-me", "chicktrack", "admin", "password", "minioadmin");
	private static final int MIN_PASSWORD_LENGTH = 12;

	private final String issuer;
	private final String adminPassword;
	private final String databasePassword;
	private final String signingKey;
	private final boolean allowGeneratedKey;

	public ProductionSafetyCheck(
			@Value("${app.oauth.issuer}") String issuer,
			@Value("${app.oauth.admin.password}") String adminPassword,
			@Value("${spring.datasource.password}") String databasePassword,
			@Value("${app.oauth.jwk.private-key:}") String signingKey,
			@Value("${app.oauth.jwk.allow-generated:true}") boolean allowGeneratedKey) {
		this.issuer = issuer;
		this.adminPassword = adminPassword;
		this.databasePassword = databasePassword;
		this.signingKey = signingKey;
		this.allowGeneratedKey = allowGeneratedKey;
	}

	@Override
	public void afterPropertiesSet() {
		List<String> problems = new ArrayList<>();
		if (!issuer.startsWith("https://")) {
			problems.add("OAUTH_ISSUER must be the public https:// URL of the API (got " + issuer + ")");
		}
		checkSecret("OAUTH_ADMIN_PASSWORD", adminPassword, problems);
		checkSecret("SPRING_DATASOURCE_PASSWORD (POSTGRES_PASSWORD)", databasePassword, problems);
		if (signingKey.isBlank()) {
			problems.add("OAUTH_JWK_PRIVATE_KEY must point to a PKCS#8 PEM key, e.g. file:/run/secrets/jwk.pem");
		}
		if (allowGeneratedKey) {
			problems.add("OAUTH_JWK_ALLOW_GENERATED must be false in production");
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Unsafe production configuration:\n - " + String.join("\n - ", problems));
		}
	}

	private static void checkSecret(String name, String value, List<String> problems) {
		if (value == null || value.length() < MIN_PASSWORD_LENGTH || KNOWN_DEFAULTS.contains(value.toLowerCase())) {
			problems.add(name + " must be at least " + MIN_PASSWORD_LENGTH + " characters and not a default value");
		}
	}
}
