# ChickTrack Documentation

ChickTrack is a backend for managing chicken procurement, inventory, distribution, branch receiving, payments, and reporting.

## Start Here

- [MVP Plan](mvp-plan.md)
- [Architecture](architecture.md)
- [Database](database.md)
- [Security and OAuth2](security.md)
- [Standards and Guardrails](standards-and-guardrails.md)
- [Feature Documentation](features/README.md)
- [API Documentation](api/README.md)

## Project Status

| Area | Status |
|---|---|
| Spring Boot foundation | Implemented |
| OAuth2 authorization server | Implemented |
| OAuth2 resource server | Implemented |
| JDBC-backed OAuth stores | Implemented |
| Identity schema and bootstrap admin | Implemented |
| Foundation schema | Implemented |
| Feature APIs | Planned |
| Mobile app | Deferred |

## Conventions

- Backend only for the MVP.
- PostgreSQL is the database of record.
- Flyway owns all schema changes.
- Money and weight use `BigDecimal` and PostgreSQL `NUMERIC`.
- API decimals are serialized as strings.
- Timestamps are stored in UTC.
- Business dates use the `Asia/Colombo` time zone.
- Posted documents are reversed, not edited.
- Every important action is auditable.
