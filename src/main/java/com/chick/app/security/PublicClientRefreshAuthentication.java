package com.chick.app.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Lets a public client (no secret, e.g. the mobile app) use the {@code refresh_token} grant and the
 * mobile {@link PasswordGrant}.
 * Spring only authenticates public clients on the authorization-code exchange, where PKCE proves
 * the caller; a refresh request is identified by {@code client_id} alone, and the rotated,
 * single-use refresh token is the credential.
 */
final class PublicClientRefreshAuthentication {

	private PublicClientRefreshAuthentication() {
	}

	static final class Converter implements AuthenticationConverter {

		@Override
		public Authentication convert(HttpServletRequest request) {
			String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
			if (!(AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)
					|| PasswordGrant.GRANT_TYPE.getValue().equals(grantType))
					|| request.getHeader(HttpHeaders.AUTHORIZATION) != null
					|| request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null
					|| request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null) {
				return null;
			}
			String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
			if (clientIds == null || clientIds.length != 1 || !StringUtils.hasText(clientIds[0])) {
				return null;
			}
			return new OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null, Map.of());
		}
	}

	static final class Provider implements AuthenticationProvider {

		private final RegisteredClientRepository clients;

		Provider(RegisteredClientRepository clients) {
			this.clients = clients;
		}

		@Override
		public Authentication authenticate(Authentication authentication) {
			OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) authentication;
			if (!ClientAuthenticationMethod.NONE.equals(token.getClientAuthenticationMethod())
					|| token.getCredentials() != null) {
				return null;
			}
			if (!(token.getPrincipal() instanceof String clientId)) {
				return null;
			}
			RegisteredClient client = clients.findByClientId(clientId);
			if (client == null
					|| !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
					|| !(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)
							|| client.getAuthorizationGrantTypes().contains(PasswordGrant.GRANT_TYPE))) {
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
			}
			return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
		}

		@Override
		public boolean supports(Class<?> authentication) {
			return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
		}
	}
}
