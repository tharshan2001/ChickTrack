package com.chick.app.security;

import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A user signed in with a password the owner set (new account or reset) may only read
 * {@code GET /api/v1/me} and call {@code POST /api/v1/me/password} until they choose their own.
 * The access token carries {@link #CLAIM}; the database is checked only for such tokens, so the
 * same token works again as soon as the password is changed.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	public static final String CLAIM = "pwd_change";

	private final UserAccountRepository users;

	public PasswordChangeRequiredFilter(UserAccountRepository users) {
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
				&& authentication.getPrincipal() instanceof Jwt jwt
				&& Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM))
				&& !allowed(request)
				&& stillRequired(jwt.getSubject())) {
			writeError(response, request);
			return;
		}
		chain.doFilter(request, response);
	}

	private boolean stillRequired(String username) {
		return users.findByUsername(username).map(UserAccount::isMustChangePassword).orElse(false);
	}

	private static boolean allowed(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		String method = request.getMethod();
		return ("GET".equals(method) && "/api/v1/me".equals(path))
				|| ("POST".equals(method) && "/api/v1/me/password".equals(path));
	}

	private static void writeError(HttpServletResponse response, HttpServletRequest request) throws IOException {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String requestId = request.getHeader("X-Request-Id");
		response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\","
				+ "\"message\":\"Choose a new password with POST /api/v1/me/password before continuing\","
				+ "\"fieldErrors\":null,\"requestId\":"
				+ (requestId == null ? "null" : "\"" + requestId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
				+ ",\"timestamp\":\"" + Instant.now() + "\"}");
	}
}
