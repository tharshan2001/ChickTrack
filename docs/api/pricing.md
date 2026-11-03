# Pricing API

## Status

Planned.

## Create Price

```http
POST /api/v1/daily-prices
Authorization: Bearer <token>
```

```json
{
  "chickenTypeId": "00000000-0000-0000-0000-000000000000",
  "supplierId": "00000000-0000-0000-0000-000000000000",
  "purchasePricePerKg": "850.0000",
  "sellingPricePerKg": "1100.0000",
  "effectiveFrom": "2026-01-01T00:00:00Z"
}
```

## List Prices

```http
GET /api/v1/daily-prices?chickenTypeId=...&supplierId=...&from=2026-01-01&to=2026-01-31
```

## Current Price

```http
GET /api/v1/daily-prices/current?chickenTypeId=...&supplierId=...&at=2026-01-15T10:00:00Z
```

## Price Trend

```http
GET /api/v1/daily-prices/trend?chickenTypeId=...&from=2026-01-01&to=2026-03-31&groupBy=day
```

## Rules

- Price history is immutable.
- Corrections create a new record.
- The latest effective record wins.
- A supplier-specific price takes precedence over a general price.
- Procurement and distribution store price snapshots.

## Response

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "chickenTypeId": "00000000-0000-0000-0000-000000000000",
  "supplierId": null,
  "purchasePricePerKg": "850.0000",
  "sellingPricePerKg": "1100.0000",
  "effectiveFrom": "2026-01-01T00:00:00Z",
  "createdAt": "2026-01-01T08:00:00Z"
}
```

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `CONFLICT` when the effective period overlaps an existing record.
