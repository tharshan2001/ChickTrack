package com.chick.app.sync;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores idempotency keys and the response each one produced ({@code sync_requests}). */
@Repository
public class SyncRequestRepository {

	private final JdbcTemplate jdbc;

	public SyncRequestRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<StoredRequest> find(String key) {
		return jdbc.query(
				"select request_hash, response_status, response_body::text from sync_requests where idempotency_key = ?",
				rs -> rs.next()
						? Optional.of(new StoredRequest(
								rs.getString(1),
								(Integer) rs.getObject(2),
								rs.getString(3)))
						: Optional.empty(),
				key);
	}

	/** Claims the key. Returns false when another request already holds it. */
	public boolean claim(String key, String requestHash) {
		try {
			jdbc.update("insert into sync_requests (id, idempotency_key, request_hash) values (?, ?, ?)",
					UUID.randomUUID(), key, requestHash);
			return true;
		}
		catch (DuplicateKeyException ex) {
			return false;
		}
	}

	public void complete(String key, int status, String jsonBody) {
		jdbc.update("update sync_requests set response_status = ?, response_body = cast(? as jsonb) where idempotency_key = ?",
				status, jsonBody, key);
	}

	public void release(String key) {
		jdbc.update("delete from sync_requests where idempotency_key = ? and response_status is null", key);
	}

	public record StoredRequest(String requestHash, Integer responseStatus, String responseBody) {

		public boolean completed() {
			return responseStatus != null;
		}
	}
}
