package com.chick.app.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSafetyCheckTest {

	private static final String STRONG = "q7XkPm4tRw9zLp2";

	@Test
	void acceptsASafeConfiguration() {
		assertThatCode(() -> new ProductionSafetyCheck("https://api.example.com", STRONG, STRONG + "db",
				"file:/run/secrets/jwk.pem", false).afterPropertiesSet()).doesNotThrowAnyException();
	}

	@Test
	void listsEveryProblem() {
		assertThatThrownBy(() -> new ProductionSafetyCheck("http://localhost:8090", "change-me", "chicktrack", "", true)
				.afterPropertiesSet())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("OAUTH_ISSUER")
				.hasMessageContaining("OAUTH_ADMIN_PASSWORD")
				.hasMessageContaining("SPRING_DATASOURCE_PASSWORD")
				.hasMessageContaining("OAUTH_JWK_PRIVATE_KEY")
				.hasMessageContaining("OAUTH_JWK_ALLOW_GENERATED");
	}
}
