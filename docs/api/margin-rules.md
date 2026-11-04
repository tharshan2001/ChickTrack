# Company Margin Rules API

## Status

Implemented.

## Create Rule

```http
POST /api/v1/company-margin-rules
```

Request:

```json
{
  "chickenTypeId": "33333333-3333-3333-3333-333333333333",
  "branchId": null,
  "markupPercent": "5.0000",
  "effectiveFrom": "2026-01-01T00:00:00Z"
}
```

Response:

```json
{
  "data": {
    "id": "55555555-5555-5555-5555-555555555555",
    "chickenTypeId": "33333333-3333-3333-3333-333333333333",
    "branchId": null,
    "markupPercent": "5.0000",
    "effectiveFrom": "2026-01-01T00:00:00Z",
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "timestamp": "2026-01-01T00:00:00Z"
}
```

## List Rules

```http
GET /api/v1/company-margin-rules?chickenTypeId=...&branchId=...
```

## Get Rule

```http
GET /api/v1/company-margin-rules/{id}
```

## Rules

- `markupPercent` must be zero or greater.
- `effectiveFrom` is required.
- `branchId` is optional; omit it for a rule that applies to every branch.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
