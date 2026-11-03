# Standards and Guardrails

This document defines the non-negotiable standards for ChickTrack. Every change must follow these rules.

## Architecture

- Use a modular monolith. Do not create microservices for MVP.
- Each feature owns its `controller`, `service`, `repository`, `domain`, `dto`, and `mapper` packages.
- Cross-module writes must go through services, not repositories.
- Keep controllers thin. Business rules belong in services.
- Do not put SQL in controllers or services. Use repositories and migrations.
- Do not bypass Flyway. Schema changes are never made manually.
- Do not use `hibernate.ddl-auto=create` or `update` in any non-local profile.

## Code Style

- Java 21.
- Spring Boot 4.1.2.
- Use Lombok and MapStruct consistently.
- Use constructor injection. Do not use field injection.
- Use `BigDecimal` for money and weight. Never use `double` or `float`.
- Use UUIDs for primary keys.
- Use explicit status enums or constrained string values.
- Keep methods focused and small.
- Add comments only when the reason is not obvious from the code.

## API Standards

- Base path is `/api/v1`.
- Every protected endpoint requires a bearer token.
- Use DTOs for request and response. Do not expose entities directly.
- Validate all request bodies with Jakarta Validation.
- Return the standard `ApiResponse` or `PageResponse` envelope.
- Use `BigDecimal`-compatible string values for decimals in JSON.
- Use ISO-8601 UTC timestamps.
- Business dates use `Asia/Colombo`.
- Paginate list endpoints. Default page size is 20.
- Keep endpoint paths resource-oriented and plural.

## Database Standards

- PostgreSQL is the database of record.
- Flyway owns all schema changes.
- Primary keys are `uuid`.
- Use `numeric` for money and weight.
- Weights use 3 decimals.
- Unit prices and costs use 4 decimals.
- Line amounts use 2 decimals.
- Use `timestamptz` for timestamps.
- Add foreign keys for all relationships.
- Use `CHECK` constraints for status and non-negative values.
- Use optimistic locking with `version`.
- Use soft deactivation flags instead of hard deletes.
- Posted documents are never edited. Use reversal documents.

## Security Standards

- OAuth2 Authorization Code + PKCE is the only mobile login flow.
- Access tokens expire in 15 minutes.
- Refresh tokens expire in 30 days and rotate.
- OAuth2 clients, authorizations, and consents are stored in PostgreSQL.
- Passwords are hashed with bcrypt or Argon2.
- Role checks and branch scoping are enforced in services.
- Branch-scoped users must not access another branch by changing an ID.
- Secrets are never committed to Git.
- HTTPS is required in production.
- Actuator endpoints are restricted in production.

## Error Handling

- Use `BusinessException` for expected business failures.
- Use `ResourceNotFoundException` for missing resources.
- Map all exceptions through `GlobalExceptionHandler`.
- Return the standard error envelope.
- Do not expose stack traces to clients.
- Log unexpected errors with enough context for debugging.

## Audit and Idempotency

- Audit all important actions.
- Audit records are append-only.
- Never store passwords or secrets in audit records.
- Mobile-originated mutations must support idempotency keys.
- Duplicate idempotent requests return the original response.
- Reusing an idempotency key with a different payload is rejected.
- Document numbers are allocated transactionally by type and year.

## Inventory Guardrails

- `inventory_movements` is the source of truth.
- `stock_batches.remaining_weight` is a transactional projection.
- Remaining weight must never be negative.
- FIFO allocation must lock candidate batches.
- Distribution cannot exceed available stock.
- The final issue from a batch consumes its remaining cost.
- Posted inventory changes are reversed, not edited.

## Pricing Guardrails

- Price history is immutable.
- Corrections create new records.
- Procurement and distribution store price snapshots.
- Branch-specific selling prices use branch, chicken type, and effective date.
- A supplier-specific price takes precedence over a general price.
- Changing a price never changes existing transactions.

## Testing Standards

- Unit tests are required for calculations.
- Integration tests use Testcontainers PostgreSQL.
- Test database constraints and migrations.
- Test concurrency for stock allocation.
- Test role and branch isolation through direct API calls.
- Do not weaken tests to make code pass.

## Git Standards

- Commit in small, logical units.
- Use clear commit messages.
- Do not commit `.env` or secrets.
- Do not commit generated build output.
- Do not push unless explicitly requested.
- Review `git status` and `git diff` before committing.

## Documentation Standards

- Update `docs/` when behavior changes.
- Keep feature docs and API docs aligned with implementation.
- Document new endpoints, rules, and errors.
- Keep examples consistent with actual request and response shapes.

## Deployment Standards

- Use Docker Compose for local development.
- Use environment variables for configuration.
- Run Flyway at deployment.
- Back up PostgreSQL daily.
- Test restore procedures before release.
- Use Sentry and actuator health checks in production.

## Prohibited Patterns

- No floating-point money or weight.
- No entity exposure in API responses.
- No direct cross-module repository access.
- No schema changes outside Flyway.
- No hard deletes for business records.
- No editing posted documents.
- No client-side security as the only protection.
- No secrets in source control.
