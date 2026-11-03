CREATE TABLE audit_logs (
    id uuid PRIMARY KEY,
    actor_id uuid,
    actor_username varchar(100),
    action varchar(100) NOT NULL,
    entity_type varchar(100) NOT NULL,
    entity_id varchar(100),
    old_values jsonb,
    new_values jsonb,
    metadata jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_type, entity_id);
CREATE INDEX audit_logs_created_at_idx ON audit_logs (created_at);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_id);

CREATE TABLE document_sequences (
    document_type varchar(50) NOT NULL,
    year integer NOT NULL,
    next_value bigint NOT NULL DEFAULT 1,
    PRIMARY KEY (document_type, year)
);

CREATE TABLE sync_requests (
    id uuid PRIMARY KEY,
    idempotency_key varchar(100) NOT NULL,
    request_hash varchar(64),
    response_status integer,
    response_body jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX sync_requests_idempotency_key_idx
    ON sync_requests (idempotency_key);
CREATE INDEX sync_requests_created_at_idx ON sync_requests (created_at);
