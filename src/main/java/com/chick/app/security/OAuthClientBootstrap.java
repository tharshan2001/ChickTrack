package com.chick.app.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
public class OAuthClientBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(OAuthClientBootstrap.class);

	private final RegisteredClientRepository clients;
	private final String clientId;
	private final String redirectUri;

	public OAuthClientBootstrap(
			RegisteredClientRepository clients,
			@Value("${app.oauth.client.mobile.id:chicktrack-mobile}") String clientId,
			@Value("${app.oauth.client.mobile.redirect-uri:chicktrack://oauth2/callback}") String redirectUri) {
		this.clients = clients;
		this.clientId = clientId;
		this.redirectUri = redirectUri;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (clients.findByClientId(clientId) != null) {
			return;
		}

		RegisteredClient mobileClient = RegisteredClient.withId(clientId)
				.clientId(clientId)
				.clientName("ChickTrack Mobile")
				.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUri(redirectUri)
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.scope("api.read")
				.scope("api.write")
				.clientSettings(ClientSettings.builder()
						.requireProofKey(true)
						.requireAuthorizationConsent(true)
						.build())
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(Duration.ofMinutes(15))
						.refreshTokenTimeToLive(Duration.ofDays(30))
						.reuseRefreshTokens(false)
						.build())
				.build();

		clients.save(mobileClient);
		log.info("Registered OAuth2 client '{}'", clientId);
	}
}
