package com.chick.app.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class IdempotencyFilterTest {

	private final InMemoryRequests store = new InMemoryRequests();
	private final IdempotencyFilter filter = new IdempotencyFilter(store);
	private final AtomicInteger executions = new AtomicInteger();

	@BeforeEach
	void login() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("alice", "n/a", AuthorityUtils.NO_AUTHORITIES));
	}

	@AfterEach
	void logout() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void repeatedRequestReturnsTheStoredResponseWithoutRunningAgain() throws Exception {
		MockHttpServletResponse first = call("key-1", "{\"amount\":\"10\"}", 201);
		MockHttpServletResponse second = call("key-1", "{\"amount\":\"10\"}", 201);

		assertThat(executions).hasValue(1);
		assertThat(second.getStatus()).isEqualTo(201);
		assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
		assertThat(second.getHeader("Idempotent-Replayed")).isEqualTo("true");
	}

	@Test
	void sameKeyWithADifferentBodyIsRejected() throws Exception {
		call("key-2", "{\"amount\":\"10\"}", 201);
		MockHttpServletResponse second = call("key-2", "{\"amount\":\"99\"}", 201);

		assertThat(second.getStatus()).isEqualTo(422);
		assertThat(second.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
		assertThat(executions).hasValue(1);
	}

	@Test
	void conflictsAndServerErrorsAreNotStoredSoTheClientCanRetry() throws Exception {
		call("key-3", "{}", 409);
		call("key-3", "{}", 500);
		call("key-3", "{}", 201);
		MockHttpServletResponse replay = call("key-3", "{}", 201);

		assertThat(executions).hasValue(3);
		assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");
	}

	@Test
	void keysAreScopedToTheUser() throws Exception {
		call("shared", "{}", 201);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("bob", "n/a", AuthorityUtils.NO_AUTHORITIES));
		call("shared", "{}", 201);

		assertThat(executions).hasValue(2);
	}

	@Test
	void requestsWithoutAKeyPassThrough() throws Exception {
		call(null, "{}", 201);
		call(null, "{}", 201);
		assertThat(executions).hasValue(2);
		assertThat(store.rows).isEmpty();
	}

	private MockHttpServletResponse call(String key, String body, int status) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/branch-settlements");
		if (key != null) {
			request.addHeader(IdempotencyFilter.HEADER, key);
		}
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (req, res) -> {
			executions.incrementAndGet();
			assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
			res.setContentType("application/json");
			((jakarta.servlet.http.HttpServletResponse) res).setStatus(status);
			res.getWriter().write("{\"run\":" + executions.get() + "}");
		});
		return response;
	}

	private static final class InMemoryRequests extends SyncRequestRepository {

		final Map<String, SyncRequestRepository.StoredRequest> rows = new HashMap<>();

		InMemoryRequests() {
			super(null);
		}

		@Override
		public Optional<StoredRequest> find(String key) {
			return Optional.ofNullable(rows.get(key));
		}

		@Override
		public boolean claim(String key, String requestHash) {
			return rows.putIfAbsent(key, new StoredRequest(requestHash, null, null)) == null;
		}

		@Override
		public void complete(String key, int status, String jsonBody) {
			rows.put(key, new StoredRequest(rows.get(key).requestHash(), status, jsonBody));
		}

		@Override
		public void release(String key) {
			if (rows.containsKey(key) && !rows.get(key).completed()) {
				rows.remove(key);
			}
		}
	}
}
