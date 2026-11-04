# API Documentation

## Base URL

```text
http://localhost:8090
```

Business endpoints use `/api/v1`.

## Authentication

Protected endpoints require a bearer access token:

```http
Authorization: Bearer <access_token>
```

Obtain tokens through the OAuth2 authorization server. See [Authentication API](auth.md).

## Content Types

- JSON requests and responses: `application/json`
- Token endpoint: `application/x-www-form-urlencoded`
- Exports: `text/csv` or `application/pdf`

## Headers

| Header | Purpose |
|---|---|
| `Authorization` | Bearer access token |
| `X-Request-Id` | Client correlation ID, echoed in errors |
| `Idempotency-Key` | Prevents duplicate mutations |

## Decimals

Money and weight values are serialized as strings:

```json
{
  "weightKg": "990.000",
  "unitCost": "838.3838",
  "amount": "830000.00"
}
```

Clients must not parse these as floating-point values.

## Dates and Times

- Timestamps are ISO-8601 UTC instants.
- Business dates use `Asia/Colombo`.
- Report date ranges are inclusive of the start date and exclusive of the end date unless documented otherwise.

## Pagination

List endpoints accept:

```text
?page=0&size=20&sort=createdAt,desc
```

Paginated responses:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## Errors

Errors use the standard format described in [Errors](errors.md).

## Endpoint Status

| Area | Base Path | Status |
|---|---|---|
| OAuth2 | `/oauth2/*` | Implemented |
| OIDC | `/.well-known/*`, `/userinfo` | Implemented |
| Users and roles | `/api/v1/users`, `/api/v1/roles`, `/api/v1/me` | Implemented ([docs](users.md)) |
| Master data | `/api/v1/branches`, `/suppliers`, `/chicken-types`, `/expense-categories` | Implemented |
| Pricing | `/api/v1/daily-prices` | Implemented |
| Company margins | `/api/v1/company-margin-rules` | Implemented |
| Procurement | `/api/v1/procurements` | Implemented |
| Inventory | `/api/v1/stock-batches`, `/inventory-movements`, `/stock-adjustments` | Implemented ([docs](inventory.md)) |
| Distribution | `/api/v1/distributions` | Implemented |
| Branch sales | `/api/v1/branch-sales` | Implemented |
| Finance | `/api/v1/branch-settlements`, `/api/v1/branches/{id}/balance` | Implemented |
| Reports | `/api/v1/reports`, `/api/v1/dashboard` | Implemented |

## OpenAPI

- JSON: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`
