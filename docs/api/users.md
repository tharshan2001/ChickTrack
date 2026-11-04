# Users and Roles API

## Status

Implemented. All endpoints except `/me` require `OWNER`.

## Current User

```http
GET /api/v1/me
POST /api/v1/me/password
```

```json
{ "currentPassword": "old-secret", "newPassword": "new-secret-123" }
```

Changing a password signs the user out of every device: stored refresh tokens are revoked.

## Roles

```http
GET /api/v1/roles
```

Roles: `OWNER`, `PROCUREMENT_MANAGER`, `WAREHOUSE_MANAGER`, `DISTRIBUTION_MANAGER`, `DRIVER`, `BRANCH_RECEIVER`, `ACCOUNTANT`, `VIEWER`.

## Users

```http
GET  /api/v1/users?active=true&role=BRANCH_RECEIVER&branchId=...&search=kandy&page=0&size=20
GET  /api/v1/users/{id}
POST /api/v1/users
PUT  /api/v1/users/{id}
PUT  /api/v1/users/{id}/roles
PUT  /api/v1/users/{id}/branches
PATCH /api/v1/users/{id}/status
POST /api/v1/users/{id}/password
```

Create:

```json
{
  "username": "kandy.receiver",
  "password": "initial-pass-123",
  "fullName": "Kandy Receiver",
  "email": "kandy@example.com",
  "phone": "+94 77 000 0000",
  "roles": ["BRANCH_RECEIVER"],
  "branchIds": ["11111111-1111-1111-1111-111111111111"]
}
```

Response:

```json
{
  "data": {
    "id": "22222222-2222-2222-2222-222222222222",
    "username": "kandy.receiver",
    "fullName": "Kandy Receiver",
    "email": "kandy@example.com",
    "phone": "+94 77 000 0000",
    "active": true,
    "roles": ["BRANCH_RECEIVER"],
    "branchIds": ["11111111-1111-1111-1111-111111111111"],
    "version": 0,
    "createdAt": "2026-11-04T10:00:00Z",
    "updatedAt": "2026-11-04T10:00:00Z"
  },
  "timestamp": "2026-11-04T10:00:00Z"
}
```

Other bodies:

| Endpoint | Body |
|---|---|
| `PUT /users/{id}` | `{ "fullName", "email", "phone" }` |
| `PUT /users/{id}/roles` | `{ "roles": ["ACCOUNTANT"] }` (replaces all roles) |
| `PUT /users/{id}/branches` | `{ "branchIds": [...] }` (replaces all branches; `[]` removes them) |
| `PATCH /users/{id}/status` | `{ "active": false }` |
| `POST /users/{id}/password` | `{ "newPassword": "..." }` |

## Rules

- Usernames are unique (case-insensitive) and use letters, digits, `.`, `_`, `-`.
- Passwords are 8 to 128 characters and stored as hashes; they are never returned or audited.
- Only active branches can be assigned.
- You cannot deactivate yourself or remove your own `OWNER` role.
- At least one active `OWNER` must remain.
- Deactivating a user, changing roles or resetting a password revokes the user's refresh tokens. Access tokens already issued expire within 15 minutes.
- Branch access follows the assigned branches; see [Security](../security.md#branch-scoping).

## Errors

- `VALIDATION_FAILED` (bad body, unknown role)
- `RESOURCE_NOT_FOUND`
- `CONFLICT` (username exists)
- `BUSINESS_RULE_VIOLATION` (self-lockout, last owner, inactive branch, wrong current password)
- `FORBIDDEN`
