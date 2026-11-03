# Authentication and OAuth2

## Status

Implemented foundation.

## Purpose

Allow staff to authenticate once and access ChickTrack APIs according to their role and branch assignment.

## Actors

- Owner
- Procurement Manager
- Warehouse Manager
- Distribution Manager
- Driver
- Branch Receiver
- Accountant
- Viewer

## Flow

1. Client starts Authorization Code + PKCE at `/oauth2/authorize`.
2. User signs in through `/login`.
3. Authorization server redirects with a code.
4. Client exchanges the code at `/oauth2/token`.
5. Client calls `/api/**` with a bearer token.
6. Client refreshes tokens at `/oauth2/token`.

## Rules

- Public mobile client uses PKCE and has no client secret.
- Access tokens expire after 15 minutes.
- Refresh tokens expire after 30 days and rotate.
- OAuth2 clients, authorizations, and consents are stored in PostgreSQL.
- The first admin is created on startup if no users exist.
- Users are disabled through `users.active`, not deleted.
- Role checks and branch scoping are enforced server-side.

## Data

- `users`
- `roles`
- `user_roles`
- `user_branches`
- `oauth2_registered_client`
- `oauth2_authorization`
- `oauth2_authorization_consent`

## API

- `GET /oauth2/authorize`
- `POST /oauth2/token`
- `GET /oauth2/jwks`
- `POST /oauth2/revoke`
- `POST /oauth2/introspect`
- `GET /.well-known/openid-configuration`
- `GET /userinfo`
- `GET /login`

## Acceptance Criteria

- A valid login returns access and refresh tokens.
- An invalid or expired token receives `401`.
- A user without the required role receives `403`.
- A branch-scoped user cannot access another branch by changing an ID.
- OAuth2 client and token records survive application restart.
