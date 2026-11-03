# Master Data API

## Status

Planned. Identity tables and bootstrap admin exist.

All endpoints are under `/api/v1` and require a bearer token.

## Branches

| Method | Path | Role |
|---|---|---|
| `GET` | `/branches` | Any authenticated |
| `GET` | `/branches/{id}` | Any authenticated |
| `POST` | `/branches` | `OWNER` |
| `PUT` | `/branches/{id}` | `OWNER` |
| `PATCH` | `/branches/{id}/status` | `OWNER` |

Example create request:

```json
{
  "code": "COL",
  "name": "Colombo Branch",
  "address": "Colombo",
  "phone": "+94 11 000 0000"
}
```

## Users

| Method | Path | Role |
|---|---|---|
| `GET` | `/users` | `OWNER` |
| `GET` | `/users/{id}` | `OWNER` |
| `POST` | `/users` | `OWNER` |
| `PUT` | `/users/{id}` | `OWNER` |
| `PATCH` | `/users/{id}/status` | `OWNER` |
| `PUT` | `/users/{id}/roles` | `OWNER` |
| `PUT` | `/users/{id}/branches` | `OWNER` |

## Roles

| Method | Path | Role |
|---|---|---|
| `GET` | `/roles` | `OWNER` |

Roles are seeded by Flyway.

## Suppliers

| Method | Path | Role |
|---|---|---|
| `GET` | `/suppliers` | Any authenticated |
| `GET` | `/suppliers/{id}` | Any authenticated |
| `POST` | `/suppliers` | `PROCUREMENT_MANAGER`, `OWNER` |
| `PUT` | `/suppliers/{id}` | `PROCUREMENT_MANAGER`, `OWNER` |
| `PATCH` | `/suppliers/{id}/status` | `PROCUREMENT_MANAGER`, `OWNER` |

## Chicken Types

| Method | Path | Role |
|---|---|---|
| `GET` | `/chicken-types` | Any authenticated |
| `POST` | `/chicken-types` | `OWNER`, `PROCUREMENT_MANAGER` |
| `PUT` | `/chicken-types/{id}` | `OWNER`, `PROCUREMENT_MANAGER` |
| `PATCH` | `/chicken-types/{id}/status` | `OWNER`, `PROCUREMENT_MANAGER` |

## Expense Categories

| Method | Path | Role |
|---|---|---|
| `GET` | `/expense-categories` | Any authenticated |
| `POST` | `/expense-categories` | `OWNER`, `ACCOUNTANT` |
| `PUT` | `/expense-categories/{id}` | `OWNER`, `ACCOUNTANT` |
| `PATCH` | `/expense-categories/{id}/status` | `OWNER`, `ACCOUNTANT` |

## Common Query Parameters

```text
?page=0&size=20&sort=name,asc&active=true&search=col
```

## Common Errors

- `VALIDATION_FAILED` for invalid input.
- `RESOURCE_NOT_FOUND` for unknown IDs.
- `CONFLICT` for duplicate codes or usernames.
- `FORBIDDEN` for missing roles or branches.
