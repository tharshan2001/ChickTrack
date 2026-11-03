# ChickTrack

Backend for managing chicken procurement, inventory, distribution, branch receiving, payments, and reporting.

The MVP is backend-only. The mobile client is deferred.

## Status

The project foundation is in place:

- Spring Boot 4.1.2 on Java 21
- PostgreSQL with Flyway migrations
- OAuth2 authorization server and resource server
- JDBC-backed OAuth2 clients, authorizations, and consents
- Identity schema with bootstrap admin
- Foundation schema for audit logs, document sequences, and idempotency
- OpenAPI and Swagger UI
- Docker Compose for PostgreSQL and MinIO
- Feature and API documentation

Business modules are the next implementation phase.

## Tech Stack

| Area | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.1.2 |
| Database | PostgreSQL |
| Migrations | Flyway |
| Security | Spring Security, Spring Authorization Server |
| API docs | springdoc OpenAPI / Swagger UI |
| Storage | S3-compatible storage (MinIO locally) |
| Reports | OpenPDF, Apache POI, Commons CSV |
| Push | Firebase Admin SDK |
| Testing | JUnit 5, Spring Boot Test, Testcontainers |

## Prerequisites

- Java 21
- Docker and Docker Compose
- Git

## Quick Start

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and replace placeholder secrets, especially `OAUTH_ADMIN_PASSWORD` and database credentials.

### 2. Start infrastructure

```bash
docker compose up -d
```

This starts:

- PostgreSQL on `localhost:5432`
- MinIO on `localhost:9000`
- MinIO console on `localhost:9001`

### 3. Run the application

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

The `.env` file is used by Docker Compose and can also be loaded into the shell before running the application locally.

The API starts on `http://localhost:8080`.

Flyway creates the schema on startup. On first run, a bootstrap admin is created from `OAUTH_ADMIN_USERNAME` and `OAUTH_ADMIN_PASSWORD`.

### 4. Open the API surface

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/actuator/health` | Health check |
| `http://localhost:8080/.well-known/openid-configuration` | OIDC discovery |
| `http://localhost:8080/login` | Form login for OAuth2 flow |

## OAuth2

ChickTrack is both the authorization server and the resource server.

- Authorization Code + PKCE
- Public mobile client: `chicktrack-mobile`
- Access token lifetime: 15 minutes
- Refresh token lifetime: 30 days, rotated
- Protected API prefix: `/api/**`

See [Security and OAuth2](docs/security.md) and [Authentication API](docs/api/auth.md).

## Configuration

Configuration is read from environment variables with local defaults.

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/chicktrack` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `chicktrack` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `chicktrack` | Database password |
| `OAUTH_ISSUER` | `http://localhost:8080` | OAuth2 issuer |
| `OAUTH_ADMIN_USERNAME` | `admin` | Bootstrap admin |
| `OAUTH_ADMIN_PASSWORD` | `change-me` | Bootstrap admin password |
| `OAUTH_MOBILE_CLIENT_ID` | `chicktrack-mobile` | Mobile client ID |
| `OAUTH_MOBILE_REDIRECT_URI` | `chicktrack://oauth2/callback` | Mobile redirect URI |
| `AWS_S3_BUCKET` | `chicktrack-dev` | Attachment bucket |
| `SENTRY_DSN` | empty | Sentry DSN |

Never commit `.env` or production secrets.

## Database

Flyway migrations live in `src/main/resources/db/migration`.

| Migration | Purpose |
|---|---|
| `V1__oauth2.sql` | OAuth2 JDBC tables |
| `V2__identity.sql` | Users, roles, branches |
| `V3__foundation.sql` | Audit, sequences, idempotency |

See [Database](docs/database.md).

## Testing

```bash
./mvnw test
```

Integration tests use Testcontainers PostgreSQL. If Docker is unavailable, container-backed tests are skipped.

Compile only:

```bash
./mvnw -DskipTests compile
```

## Project Structure

```text
.
├── src/main/java/com/chick/app/
│   ├── common/          API envelopes, errors, base entities
│   ├── config/          JPA auditing, OpenAPI
│   ├── security/        OAuth2 server, resource server, client bootstrap
│   ├── identity/        users, roles, user details, bootstrap admin
│   └── branch/          branches
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/
├── docs/                Features, API, architecture, security
├── docker-compose.yml
└── pom.xml
```

## Documentation

- [Documentation Index](docs/README.md)
- [MVP Plan](docs/mvp-plan.md)
- [Architecture](docs/architecture.md)
- [Database](docs/database.md)
- [Security and OAuth2](docs/security.md)
- [Features](docs/features/README.md)
- [API](docs/api/README.md)

## MVP Roadmap

1. Foundation and OAuth persistence
2. Identity and master data
3. Daily prices
4. Procurement and receiving
5. Inventory and FIFO
6. Distribution and branch receiving
7. Payments and settlements
8. Dashboard and reports
9. Hardening and pilot

## Security Notes

- Change the bootstrap admin password before any shared deployment.
- Replace the generated development JWK with a persistent signing key before production.
- Use HTTPS in production.
- Keep OAuth and storage credentials out of Git.
- Restrict actuator endpoints in production.
- Back up PostgreSQL and test restores regularly.
