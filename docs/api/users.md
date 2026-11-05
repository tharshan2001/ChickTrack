# Users and Roles API

## Status

Implemented. All endpoints except `/me` require `OWNER`.

## The Owner (Main Admin)

There is exactly one owner. `IdentityBootstrap` seeds it on startup when no owner exists, from `OAUTH_ADMIN_USERNAME` and `OAUTH_ADMIN_PASSWORD`. The owner creates every other user, gives them roles and branches, hands out their credentials, and resets their passwords.

- `OWNER` cannot be assigned to anyone else (create or role change).
- The owner account cannot be demoted or deactivated, and its password cannot be reset through `/users/{id}/password`; the owner uses `POST /me/password`.

## Current User

```http
GET /api/v1/me
POST /api/v1/me/password
```

```json
{ "currentPassword": "old-secret", "newPassword": "new-secret-123" }
```

Changing a password signs the user out of every device: stored refresh tokens are revoked. It also clears `mustChangePassword`.

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
  "fullName": "Kandy Receiver",
  "email": "kandy@example.com",
  "phone": "+94 77 000 0000",
  "roles": ["BRANCH_RECEIVER"],
  "branchIds": ["11111111-1111-1111-1111-111111111111"]
}
```

`password` is optional. Leave it out and the server generates a 12-character temporary password and returns it once as `temporaryPassword`; it is never stored in clear, audited or shown again. Send `password` to set one yourself (no `temporaryPassword` comes back). `mustChangePassword` is optional and defaults to `true`.

Response (`201`):

```json
{
  "data": {
    "id": "22222222-2222-2222-2222-222222222222",
    "username": "kandy.receiver",
    "fullName": "Kandy Receiver",
    "email": "kandy@example.com",
    "phone": "+94 77 000 0000",
    "active": true,
    "mustChangePassword": true,
    "roles": ["BRANCH_RECEIVER"],
    "branchIds": ["11111111-1111-1111-1111-111111111111"],
    "version": 0,
    "createdAt": "2026-11-04T10:00:00Z",
    "updatedAt": "2026-11-04T10:00:00Z",
    "temporaryPassword": "q7XkPm4tRw9z"
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
| `POST /users/{id}/password` | optional `{ "newPassword", "mustChangePassword" }`; no body = generate one. Returns the user plus `temporaryPassword` when generated |

## Rules

- Usernames are unique (case-insensitive) and use letters, digits, `.`, `_`, `-`.
- Passwords are 8 to 128 characters and stored as hashes; they are never returned or audited.
- Only active branches can be assigned.
- `OWNER` is never assigned; the owner account cannot be demoted, deactivated or reset through `/users` (see above).
- Generated passwords use upper, lower and digits, without look-alikes (`0 O 1 l I`).
- `Idempotency-Key` is ignored on `POST /users` and `POST /users/{id}/password`, so a temporary password is never stored for replay. A retried create returns `409` (username exists); a retried reset issues a new password.

## First Sign-In

A user with `mustChangePassword: true` gets a token whose `pwd_change` claim is `true`. Until they change their password, only `GET /me` and `POST /me/password` work; every other `/api/**` call returns `403 PASSWORD_CHANGE_REQUIRED`. The same token works again as soon as the password is changed. The mobile app should read `mustChangePassword` from `/me` after sign-in and show a change-password screen.
- Deactivating a user, changing roles or resetting a password revokes the user's refresh tokens. Access tokens already issued expire within 15 minutes.
- Branch access follows the assigned branches; see [Security](../security.md#branch-scoping).

## Errors

- `VALIDATION_FAILED` (bad body, unknown role)
- `RESOURCE_NOT_FOUND`
- `CONFLICT` (username exists)
- `BUSINESS_RULE_VIOLATION` (owner protections, inactive branch, wrong current password)
- `FORBIDDEN`
- `PASSWORD_CHANGE_REQUIRED` (403, signed in with a temporary password)
