package com.chick.app.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	@Order(1)
	SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
				new OAuth2AuthorizationServerConfigurer();
		http
				.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
				.with(authorizationServerConfigurer, (authorizationServer) ->
						authorizationServer.oidc(Customizer.withDefaults()))
				.authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
				.exceptionHandling((exceptions) -> exceptions.defaultAuthenticationEntryPointFor(
						new LoginUrlAuthenticationEntryPoint("/login"),
						new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatcher("/api/**")
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers("/api/public/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer((oauth2) -> oauth2.jwt(jwt ->
						jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	@Bean
	@Order(3)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers(
								"/error",
								"/actuator/health",
								"/actuator/info",
								"/v3/api-docs/**",
								"/swagger-ui/**",
								"/swagger-ui.html")
						.permitAll()
						.anyRequest().authenticated())
				.formLogin(Customizer.withDefaults());
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
		return new JdbcRegisteredClientRepository(jdbcOperations);
	}

	@Bean
	OAuth2AuthorizationService authorizationService(
			JdbcOperations jdbcOperations,
			RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
	}

	@Bean
	OAuth2AuthorizationConsentService authorizationConsentService(
			JdbcOperations jdbcOperations,
			RegisteredClientRepository registeredClientRepository) {
		return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
	}

	@Bean
	JWKSource<SecurityContext> jwkSource() {
		KeyPair keyPair = generateRsaKey();
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
		return new ImmutableJWKSet<>(new JWKSet(rsaKey));
	}

	@Bean
	JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	@Bean
	OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
		return context -> {
			if (context.getTokenType() == OAuth2TokenType.ACCESS_TOKEN) {
				Authentication authentication = context.getPrincipal();
				Collection<String> roles = authentication.getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.filter(authority -> authority.startsWith("ROLE_"))
						.map(authority -> authority.substring(5))
						.collect(Collectors.toSet());
				context.getClaims().claim("roles", roles);
			}
		};
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
			Collection<String> roles = jwt.getClaimAsStringList("roles");
			if (roles != null) {
				for (String role : roles) {
					authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
				}
			}
			return authorities;
		});
		return converter;
	}

	@Bean
	AuthorizationServerSettings authorizationServerSettings(
			@Value("${app.oauth.issuer:http://localhost:8080}") String issuer) {
		return AuthorizationServerSettings.builder().issuer(issuer).build();
	}

	private static KeyPair generateRsaKey() {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			return keyPairGenerator.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to generate RSA key", ex);
		}
	}
}
