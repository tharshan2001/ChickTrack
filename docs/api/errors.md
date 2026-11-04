# API Errors

## Format

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "fieldErrors": {
    "code": "must not be blank"
  },
  "requestId": "client-correlation-id",
  "timestamp": "2026-01-01T00:00:00Z"
}
```

`fieldErrors` is `null` when not applicable.

## Standard Codes

| Code | HTTP Status | Meaning |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | Request body or parameters are invalid |
| `BUSINESS_RULE_VIOLATION` | 400 | The operation is not allowed by a business rule |
| `UNAUTHORIZED` | 401 | Missing, invalid, or expired token |
| `FORBIDDEN` | 403 | Role or branch access denied |
| `RESOURCE_NOT_FOUND` | 404 | Requested resource does not exist |
| `CONCURRENT_MODIFICATION` | 409 | The record changed in another request; reload and retry |
| `DATA_CONFLICT` | 409 | Duplicate value or conflicting reference |
| `INVALID_STATUS_TRANSITION` | 409 | Document cannot move to the requested status |
| `INSUFFICIENT_STOCK` | 409 | Available stock is below the requested weight |
| `OVER_ALLOCATION` | 409 | Payment or settlement exceeds the outstanding balance |
| `REQUEST_IN_PROGRESS` | 409 | A request with the same idempotency key is still running |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Idempotency key reused with a different payload |
| `REPORT_GENERATION_FAILED` | 500 | Export generation failed |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Idempotency Errors

A request with an existing idempotency key and an identical payload returns the original response.

A request with an existing key and a different payload returns `422`:

```json
{
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "Idempotency key was already used with a different request",
  "fieldErrors": null,
  "requestId": "client-correlation-id",
  "timestamp": "2026-01-01T00:00:00Z"
}
```

## OAuth2 Errors

OAuth2 endpoints return RFC 6749 errors:

```json
{
  "error": "invalid_grant",
  "error_description": "Invalid authorization code"
}
```
