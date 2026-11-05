package com.chick.app.sync;

import com.chick.app.sync.SyncRequestRepository.StoredRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes mutating API calls safe to retry. When a request carries an {@code Idempotency-Key}:
 * the first call runs and its response is stored; a repeat with the same body returns the stored
 * response; a repeat with a different body is rejected; a repeat while the first is still running
 * gets {@code 409}. Keys are scoped to the authenticated user.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

	public static final String HEADER = "Idempotency-Key";
	private static final int MAX_KEY_LENGTH = 100;
	private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

	/**
	 * Responses that can carry a one-time secret (a generated temporary password) are never stored
	 * or replayed, so the secret is not kept in clear in {@code sync_requests}. A retry runs again:
	 * a repeated create is refused as a duplicate username, a repeated reset issues a new password.
	 */
	private static final java.util.regex.Pattern SECRET_RESPONSES =
			java.util.regex.Pattern.compile("^/api/v1/users(/[^/]+/password)?/?$");

	private final SyncRequestRepository requests;

	public IdempotencyFilter(SyncRequestRepository requests) {
		this.requests = requests;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getHeader(HEADER) == null
				|| !METHODS.contains(request.getMethod())
				|| SECRET_RESPONSES.matcher(request.getRequestURI().substring(request.getContextPath().length())).matches();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String clientKey = request.getHeader(HEADER).trim();
		if (clientKey.isEmpty() || clientKey.length() > MAX_KEY_LENGTH) {
			writeError(response, request, 400, "VALIDATION_FAILED",
					"Idempotency-Key must be 1 to " + MAX_KEY_LENGTH + " characters");
			return;
		}

		byte[] body = request.getInputStream().readAllBytes();
		String key = sha256(principal() + "\n" + clientKey);
		String requestHash = sha256(request.getMethod() + " " + request.getRequestURI() + "?"
				+ String.valueOf(request.getQueryString()) + "\n" + new String(body, StandardCharsets.UTF_8));

		Optional<StoredRequest> existing = requests.find(key);
		if (existing.isEmpty() && !requests.claim(key, requestHash)) {
			existing = requests.find(key);
		}
		if (existing.isPresent()) {
			StoredRequest stored = existing.get();
			if (!requestHash.equals(stored.requestHash())) {
				writeError(response, request, 422, "IDEMPOTENCY_KEY_REUSED",
						"Idempotency-Key was already used with a different request");
			}
			else if (!stored.completed()) {
				writeError(response, request, 409, "REQUEST_IN_PROGRESS",
						"A request with this Idempotency-Key is still being processed");
			}
			else {
				response.setStatus(stored.responseStatus());
				response.setHeader("Idempotent-Replayed", "true");
				if (stored.responseBody() != null) {
					response.setContentType(MediaType.APPLICATION_JSON_VALUE);
					response.setCharacterEncoding(StandardCharsets.UTF_8.name());
					response.getWriter().write(stored.responseBody());
				}
			}
			return;
		}

		ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
		boolean stored = false;
		try {
			chain.doFilter(new CachedBodyRequest(request, body), cachingResponse);
			int status = cachingResponse.getStatus();
			// Conflicts and server errors are not stored, so the client can retry with the same key.
			if (status < 500 && status != 409 && status != 429) {
				requests.complete(key, status, jsonBody(cachingResponse));
				stored = true;
			}
		}
		finally {
			if (!stored) {
				requests.release(key);
			}
			cachingResponse.copyBodyToResponse();
		}
	}

	private static String jsonBody(ContentCachingResponseWrapper response) {
		byte[] content = response.getContentAsByteArray();
		String contentType = response.getContentType();
		if (content.length == 0 || contentType == null || !contentType.contains("json")) {
			return null;
		}
		return new String(content, StandardCharsets.UTF_8);
	}

	private static String principal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null ? "anonymous" : authentication.getName();
	}

	private static void writeError(HttpServletResponse response, HttpServletRequest request, int status,
			String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String requestId = request.getHeader("X-Request-Id");
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\","
				+ "\"fieldErrors\":null,\"requestId\":" + (requestId == null ? "null" : "\"" + escape(requestId) + "\"")
				+ ",\"timestamp\":\"" + Instant.now() + "\"}");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Lets the body be read again after it was hashed. */
	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream in = new ByteArrayInputStream(body);
			return new ServletInputStream() {
				@Override
				public int read() {
					return in.read();
				}

				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}
}
