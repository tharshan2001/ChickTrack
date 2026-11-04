package com.chick.app.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
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
	private final Set<String> redirectUris;

	public OAuthClientBootstrap(
			RegisteredClientRepository clients,
			@Value("${app.oauth.client.mobile.id:chicktrack-mobile}") String clientId,
			@Value("${app.oauth.client.mobile.redirect-uri:chicktrack://oauth2/callback}") String redirectUri) {
		this.clients = clients;
		this.clientId = clientId;
		// Comma-separated, e.g. "chicktrack://oauth2/callback,exp://192.168.1.20:8081/--/oauth2/callback".
		this.redirectUris = new LinkedHashSet<>(Arrays.stream(redirectUri.split(","))
				.map(String::trim)
				.filter(uri -> !uri.isEmpty())
				.toList());
	}

	@Override
	public void run(ApplicationArguments args) {
		RegisteredClient existing = clients.findByClientId(clientId);
		if (existing != null) {
			// Keep the configured redirect URIs in step with the environment (a new LAN address for
			// Expo Go, say) without editing the database by hand.
			if (!existing.getRedirectUris().equals(redirectUris)) {
				clients.save(RegisteredClient.from(existing)
						.redirectUris(uris -> {
							uris.clear();
							uris.addAll(redirectUris);
						})
						.build());
				log.info("Updated redirect URIs of OAuth2 client '{}' to {}", clientId, redirectUris);
			}
			return;
		}

		RegisteredClient mobileClient = RegisteredClient.withId(clientId)
				.clientId(clientId)
				.clientName("ChickTrack Mobile")
				.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUris(uris -> uris.addAll(redirectUris))
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
