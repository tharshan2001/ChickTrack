package com.chick.app.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only writes to {@code audit_logs}. */
@Repository
public class AuditLogRepository {

	private final JdbcTemplate jdbc;

	public AuditLogRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void append(UUID actorId, String actorUsername, String action, String entityType, String entityId,
			String newValuesJson, String metadataJson) {
		jdbc.update("""
				insert into audit_logs (id, actor_id, actor_username, action, entity_type, entity_id, new_values, metadata)
				values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb))
				""", UUID.randomUUID(), actorId, actorUsername, action, entityType, entityId, newValuesJson, metadataJson);
	}
}
