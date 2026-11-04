package com.chick.app.security;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Lets the mobile app sign in with a username and password typed on its own screen.
 *
 * <p>The app POSTs to {@code /oauth2/token} with {@code grant_type} {@link #GRANT_TYPE},
 * {@code client_id}, {@code username} and {@code password}. The response is the same as for the
 * authorization-code flow: a JWT access token (with the user's roles) and a rotating refresh token,
 * stored as an {@link OAuth2Authorization} so refresh, revocation and "sign out everywhere" on a
 * role or password change keep working. Only clients registered with this grant type may use it.
 */
public final class PasswordGrant {

	public static final AuthorizationGrantType GRANT_TYPE =
			new AuthorizationGrantType("urn:chicktrack:params:oauth:grant-type:password");

	private PasswordGrant() {
	}

	/** The token request, carrying the typed credentials until they are checked. */
	static final class Token extends OAuth2AuthorizationGrantAuthenticationToken {

		private final String username;
		private final String password;
		private final Set<String> scopes;

		Token(String username, String password, Set<String> scopes, Authentication clientPrincipal) {
			super(GRANT_TYPE, clientPrincipal, Map.of());
			this.username = username;
			this.password = password;
			this.scopes = Set.copyOf(scopes);
		}

		String username() {
			return username;
		}

		String password() {
			return password;
		}

		Set<String> scopes() {
			return scopes;
		}
	}

	static final class Converter implements AuthenticationConverter {

		@Override
		public Authentication convert(HttpServletRequest request) {
			if (!GRANT_TYPE.getValue().equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
				return null;
			}
			String username = single(request, "username");
			String password = single(request, "password");
			String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
			Set<String> scopes = StringUtils.hasText(scope)
					? new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")))
					: Set.of();
			Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
			return new Token(username.trim(), password, scopes, clientPrincipal);
		}

		private static String single(HttpServletRequest request, String name) {
			String[] values = request.getParameterValues(name);
			if (values == null || values.length != 1 || !StringUtils.hasText(values[0])) {
				throw new OAuth2AuthenticationException(
						new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, name + " is required", null));
			}
			return values[0];
		}
	}

	static final class Provider implements AuthenticationProvider {

		private final AuthenticationProvider users;
		private final OAuth2AuthorizationService authorizations;
		private final OAuth2TokenGenerator<? extends OAuth2Token> tokens;

		Provider(
				AuthenticationProvider users,
				OAuth2AuthorizationService authorizations,
				OAuth2TokenGenerator<? extends OAuth2Token> tokens) {
			this.users = users;
			this.authorizations = authorizations;
			this.tokens = tokens;
		}

		@Override
		public Authentication authenticate(Authentication authentication) {
			Token grant = (Token) authentication;
			if (!(grant.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientPrincipal)
					|| !clientPrincipal.isAuthenticated()
					|| clientPrincipal.getRegisteredClient() == null) {
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
			}
			RegisteredClient client = clientPrincipal.getRegisteredClient();
			if (!client.getAuthorizationGrantTypes().contains(GRANT_TYPE)) {
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
			}

			Authentication user;
			try {
				user = users.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(grant.username(), grant.password()));
			}
			catch (AuthenticationException ex) {
				// Same answer for an unknown user, a wrong password or a disabled account.
				throw new OAuth2AuthenticationException(
						new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "Wrong username or password", null));
			}
			if (user == null || !user.isAuthenticated()) {
				throw new OAuth2AuthenticationException(
						new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "Wrong username or password", null));
			}

			// API scopes only: this grant issues no ID token, and asking for "openid" would make the
			// refresh-token grant try to renew one.
			Set<String> scopes = new LinkedHashSet<>(client.getScopes());
			if (!grant.scopes().isEmpty()) {
				if (!client.getScopes().containsAll(grant.scopes())) {
					throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
				}
				scopes = new LinkedHashSet<>(grant.scopes());
			}
			scopes.remove(OidcScopes.OPENID);

			DefaultOAuth2TokenContext.Builder context = DefaultOAuth2TokenContext.builder()
					.registeredClient(client)
					.principal(user)
					.authorizationServerContext(AuthorizationServerContextHolder.getContext())
					.authorizedScopes(scopes)
					.authorizationGrantType(GRANT_TYPE)
					.authorizationGrant(grant);

			OAuth2Authorization.Builder authorization = OAuth2Authorization.withRegisteredClient(client)
					.principalName(user.getName())
					.authorizationGrantType(GRANT_TYPE)
					.authorizedScopes(scopes)
					// The refresh-token grant rebuilds the principal from this attribute.
					.attribute(Principal.class.getName(), user);

			OAuth2TokenContext accessContext = context.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
			OAuth2Token generated = tokens.generate(accessContext);
			if (generated == null) {
				throw new OAuth2AuthenticationException(
						new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "The access token could not be generated", null));
			}
			OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
					generated.getTokenValue(), generated.getIssuedAt(), generated.getExpiresAt(), scopes);
			if (generated instanceof ClaimAccessor claims) {
				authorization.token(accessToken, metadata ->
						metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims()));
			}
			else {
				authorization.accessToken(accessToken);
			}

			OAuth2RefreshToken refreshToken = null;
			if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
				OAuth2Token refresh = tokens.generate(context.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());
				if (refresh instanceof OAuth2RefreshToken token) {
					refreshToken = token;
					authorization.refreshToken(token);
				}
			}

			authorizations.save(authorization.build());
			return new OAuth2AccessTokenAuthenticationToken(client, clientPrincipal, accessToken, refreshToken);
		}

		@Override
		public boolean supports(Class<?> authentication) {
			return Token.class.isAssignableFrom(authentication);
		}
	}
}
