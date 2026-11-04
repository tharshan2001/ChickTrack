package com.chick.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI chickTrackOpenApi(@Value("${app.oauth.issuer:http://localhost:8090}") String issuer) {
		OAuthFlow authorizationCode = new OAuthFlow()
				.authorizationUrl(issuer + "/oauth2/authorize")
				.tokenUrl(issuer + "/oauth2/token")
				.refreshUrl(issuer + "/oauth2/token")
				.scopes(new Scopes()
						.addString("openid", "OpenID Connect")
						.addString("profile", "User profile")
						.addString("api.read", "Read access")
						.addString("api.write", "Write access"));

		return new OpenAPI()
				.info(new Info()
						.title("ChickTrack API")
						.version("v1")
						.description("Backend API for chicken procurement, inventory, distribution, and branch operations."))
				.addSecurityItem(new SecurityRequirement().addList("oauth2"))
				.components(new Components()
						.addSecuritySchemes("oauth2", new SecurityScheme()
								.type(SecurityScheme.Type.OAUTH2)
								.flows(new OAuthFlows().authorizationCode(authorizationCode))));
	}
}
