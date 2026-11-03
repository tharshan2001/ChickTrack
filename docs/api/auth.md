# Authentication API

## Status

Implemented.

## Discovery

```http
GET /.well-known/openid-configuration
```

Returns issuer, authorization endpoint, token endpoint, JWKS URI, scopes, and supported grant types.

## Authorization

```http
GET /oauth2/authorize
```

Example:

```text
/oauth2/authorize?response_type=code
  &client_id=chicktrack-mobile
  &redirect_uri=chicktrack://oauth2/callback
  &scope=openid profile api.read api.write
  &code_challenge=<pkce-challenge>
  &code_challenge_method=S256
  &state=<opaque-state>
```

The user signs in through `/login` and is redirected to the registered redirect URI with a code.

## Token

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
```

Authorization code exchange:

```text
grant_type=authorization_code
&code=<authorization-code>
&redirect_uri=chicktrack://oauth2/callback
&client_id=chicktrack-mobile
&code_verifier=<pkce-verifier>
```

Refresh:

```text
grant_type=refresh_token
&refresh_token=<refresh-token>
&client_id=chicktrack-mobile
```

Example response:

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "<refresh-token>",
  "scope": "openid profile api.read api.write",
  "id_token": "<oidc-id-token>"
}
```

## JWKS

```http
GET /oauth2/jwks
```

Returns the public signing keys used to verify access tokens.

## Revocation

```http
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded
```

```text
token=<access-or-refresh-token>
&token_type_hint=access_token
&client_id=chicktrack-mobile
```

## Introspection

```http
POST /oauth2/introspect
Content-Type: application/x-www-form-urlencoded
```

## User Info

```http
GET /userinfo
Authorization: Bearer <access_token>
```

## Protected API Call

```http
GET /api/v1/branches
Authorization: Bearer <access_token>
```

## Errors

OAuth2 errors follow RFC 6749, for example:

```json
{
  "error": "invalid_grant",
  "error_description": "Invalid authorization code"
}
```
