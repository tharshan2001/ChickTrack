# Audit and Idempotency

## Status

Foundation tables exist. Feature integration is planned.

## Audit

Every important action records:

- Actor ID.
- Actor username.
- Action.
- Entity type.
- Entity ID.
- Old values.
- New values.
- Metadata.
- Timestamp.

Audited actions include:

- Login and authorization failures.
- Master data changes.
- Procurement approval, receiving, and reversal.
- Stock adjustments.
- Distribution dispatch and reversal.
- Branch receiving.
- Payments and settlements.
- Price changes.
- Role and branch assignment changes.

## Audit Rules

- Audit records are append-only.
- Secrets and password hashes are never written to audit records.
- Old and new values are stored as JSON.
- Audit failures do not silently swallow business errors; they are logged.

## Idempotency

Mobile-originated mutations send an idempotency key.

1. Client generates a UUID for the operation.
2. Client sends the key in the request.
3. Server stores the key in `sync_requests`.
4. A duplicate key returns the original response.
5. A reused key with a different payload is rejected.

## Document Sequences

Human-readable references are allocated from `document_sequences` by document type and year.

Examples:

- `PRC-2026-000123`
- `DST-2026-000456`
- `RCP-2026-000789`

Allocation must be transactional and safe under concurrency.

## Data

- `audit_logs`
- `sync_requests`
- `document_sequences`

## Acceptance Criteria

- Repeating an idempotent request does not duplicate data.
- A conflicting idempotency key is rejected.
- Document numbers are unique and sequential per type/year.
- Audit records identify the actor and changed values.
