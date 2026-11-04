# Security and OAuth2

## Model

ChickTrack is both:

- An OAuth2 authorization server.
- An OAuth2 resource server protecting `/api/**`.

The first client is a public mobile client using Authorization Code with PKCE.

## Configuration

Environment variables:

| Variable | Purpose |
|---|---|
| `OAUTH_ISSUER` | Public issuer URL, default `http://localhost:8090` |
| `OAUTH_ADMIN_USERNAME` | Bootstrap admin username |
| `OAUTH_ADMIN_PASSWORD` | Bootstrap admin password |
| `OAUTH_MOBILE_CLIENT_ID` | Public mobile client ID |
| `OAUTH_MOBILE_REDIRECT_URI` | Mobile redirect URI |

Relevant properties:

```properties
app.oauth.issuer=${OAUTH_ISSUER:http://localhost:8090}
app.oauth.admin.username=${OAUTH_ADMIN_USERNAME:admin}
app.oauth.admin.password=${OAUTH_ADMIN_PASSWORD:change-me}
app.oauth.client.mobile.id=${OAUTH_MOBILE_CLIENT_ID:chicktrack-mobile}
app.oauth.client.mobile.redirect-uri=${OAUTH_MOBILE_REDIRECT_URI:chicktrack://oauth2/callback}
```

## Login Flow

1. Client opens `/oauth2/authorize` with PKCE parameters.
2. User authenticates through the Spring form login page.
3. Authorization server redirects to the registered redirect URI with an authorization code.
4. Client exchanges the code at `/oauth2/token` with the PKCE verifier.
5. Client calls `/api/**` with the bearer access token.
6. Client refreshes through `/oauth2/token` using the refresh token.

### In-app sign-in (mobile app)

The mobile app has its own username and password screen. It posts the credentials to
`/oauth2/token` with a ChickTrack extension grant (`security/PasswordGrant.java`):

```
POST /oauth2/token
grant_type=urn:chicktrack:params:oauth:grant-type:password&client_id=chicktrack-mobile&username=…&password=…
```

The response is the same as the code exchange: a JWT access token with the user's roles and a
rotating refresh token, saved as an OAuth2 authorization, so refresh, revocation and the sign-out
that follows a role or password change all work as before. Wrong username, wrong password and
disabled accounts all return `400 invalid_grant`. Only clients registered with this grant type may
use it; `OAuthClientBootstrap` adds it to the mobile client on startup. The grant issues API
scopes only (no `openid`, so no ID token).

Access tokens live for 15 minutes. Refresh tokens live for 30 days and are rotated: every refresh returns a new refresh token and the old one stops working.

The mobile client is a public client (no secret). Spring Authorization Server does not issue or accept refresh tokens for public clients by default, so ChickTrack adds two pieces in `security/`:

- `RotatingRefreshTokenGenerator` issues refresh tokens to public clients on the authorization-code exchange (PKCE has already proven the caller).
- `PublicClientRefreshAuthentication` accepts `grant_type=refresh_token` (and the in-app password grant) with only `client_id` for clients registered with `ClientAuthenticationMethod.NONE`; the single-use refresh token is the credential.

Refresh request:

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token=...&client_id=chicktrack-mobile
```

## OAuth2 Endpoints

| Endpoint | Purpose |
|---|---|
| `/oauth2/authorize` | Authorization endpoint |
| `/oauth2/token` | Token and refresh endpoint |
| `/oauth2/jwks` | Public signing keys |
| `/oauth2/revoke` | Token revocation |
| `/oauth2/introspect` | Token introspection |
| `/.well-known/openid-configuration` | OIDC discovery |
| `/userinfo` | OIDC user info |
| `/login` | Form login for authorization flow |

## Persistence

OAuth2 clients, authorizations, and consents are stored in PostgreSQL through:

- `JdbcRegisteredClientRepository`
- `JdbcOAuth2AuthorizationService`
- `JdbcOAuth2AuthorizationConsentService`

The mobile client is created on startup if it does not exist.

## Users and Roles

Users are stored in `users`. Roles are stored in `roles`, joined through `user_roles`. Branch access is stored in `user_branches`.

Seeded roles:

| Role | Scope |
|---|---|
| `OWNER` | Full system access |
| `PROCUREMENT_MANAGER` | Suppliers, procurement, purchase prices |
| `WAREHOUSE_MANAGER` | Stock, batches, adjustments |
| `DISTRIBUTION_MANAGER` | Distribution and dispatch |
| `DRIVER` | Delivery confirmation |
| `BRANCH_RECEIVER` | Branch receiving and discrepancies |
| `ACCOUNTANT` | Payments, settlements, reports |
| `VIEWER` | Read-only access |

## Bootstrap Admin

On first startup, if no users exist, `IdentityBootstrap` creates an admin using `OAUTH_ADMIN_USERNAME` and `OAUTH_ADMIN_PASSWORD`. Change the default password before any deployment.

## Branch Scoping

Branch access is enforced in the services through `BranchAccessService`, on top of the role checks in the controllers. A user with a branch-scoped role cannot read or mutate another branch by changing a URL parameter.

- `OWNER`: every branch.
- `ACCOUNTANT`, `PROCUREMENT_MANAGER`, `WAREHOUSE_MANAGER`, `DISTRIBUTION_MANAGER`: every branch, unless assigned specific branches.
- All other roles: assigned branches only. No assigned branches means no branch access (fail closed).

## API Errors

Business and validation errors return:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "fieldErrors": {
    "name": "must not be blank"
  },
  "requestId": "optional-client-request-id",
  "timestamp": "2026-01-01T00:00:00Z"
}
```

## Production Notes

- Configure a persistent signing key (`OAUTH_JWK_PRIVATE_KEY`, PKCS#8 PEM) and set `OAUTH_JWK_ALLOW_GENERATED=false` in production. Without a key the server generates one at startup and every token becomes invalid on restart.
- Use HTTPS only.
- Keep OAuth secrets out of Git.
- Restrict actuator endpoints in production.
- Audit authentication and authorization failures.
