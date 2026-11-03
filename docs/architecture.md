# Architecture

## Overview

ChickTrack is a modular monolith built with Spring Boot 4.1.2 and Java 21. It exposes a versioned REST API and acts as both an OAuth2 authorization server and resource server.

```text
Client
  |
  |  OAuth2 Authorization Code + PKCE
  v
ChickTrack authorization server
  |
  |  JWT access token
  v
ChickTrack resource server / API
  |
  +-- PostgreSQL
  +-- S3-compatible storage
  +-- Firebase Cloud Messaging
```

## Package Layout

```text
com.chick.app/
├── common/          API envelopes, errors, base entities
├── config/          JPA auditing, OpenAPI
├── security/        OAuth2 server, resource server, client bootstrap
├── identity/        users, roles, user details, bootstrap admin
├── branch/          branches
├── supplier/        suppliers                      planned
├── chicken/         chicken types, warehouses     planned
├── pricing/         daily prices                  planned
├── procurement/     procurement and receiving     planned
├── expense/         expenses and allocations      planned
├── inventory/       batches and movements         planned
├── distribution/    dispatch and FIFO             planned
├── receipt/         branch receiving              planned
├── payment/         payments and settlements      planned
├── report/          dashboard and exports         planned
├── notification/    push notifications            planned
└── sync/            idempotency                   planned
```

Each feature follows:

```text
controller -> service -> repository -> domain
                     -> dto -> mapper
```

Cross-module writes go through services, not directly through another module's repository.

## Request Flow

1. The client authenticates through the OAuth2 authorization server.
2. The client calls `/api/v1/**` with a bearer access token.
3. Spring Security validates the JWT.
4. The service checks role and branch permissions.
5. The service executes the transaction.
6. Inventory and financial invariants are enforced inside the transaction.
7. The response uses the standard API error format on failure.

## Identity and IDs

- Primary keys are UUIDs.
- Mobile-created documents will also carry a client-generated UUID and an idempotency key.
- Human-readable references use per-type/year sequences, for example `PRC-2026-000123`.
- Master data uses soft deactivation flags instead of hard deletes.

## Money, Weight, and Time

- Currency: Sri Lankan rupee (LKR).
- Weight scale: 3 decimals.
- Unit cost and price scale: 4 decimals.
- Line amount scale: 2 decimals.
- No floating-point types for money or weight.
- API decimals are serialized as strings.
- Storage is UTC; business-day reporting uses `Asia/Colombo`.

## Cross-Cutting Concerns

- Flyway migrations under `src/main/resources/db/migration`.
- JPA auditing for `created_at` and `updated_at`.
- Optimistic locking with `@Version`.
- Custom `BusinessException` hierarchy mapped to structured API errors.
- OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
- Actuator health at `/actuator/health`.

## Deployment Topology

MVP target:

```text
Caddy / Nginx (HTTPS)
  |
  +-- Spring Boot API
  +-- PostgreSQL
  +-- MinIO or S3-compatible storage
```

Backups must be encrypted and stored off-site. Restore drills are part of the release checklist.
