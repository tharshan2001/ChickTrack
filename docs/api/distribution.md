# Distribution API

## Status

Planned.

## Create Distribution

```http
POST /api/v1/distributions
Authorization: Bearer <token>
Idempotency-Key: <uuid>
```

```json
{
  "branchId": "00000000-0000-0000-0000-000000000000",
  "distributionDate": "2026-01-16",
  "items": [
    {
      "chickenTypeId": "00000000-0000-0000-0000-000000000000",
      "weightKg": "150.000",
      "sellingPricePerKg": "1100.0000"
    }
  ]
}
```

## List Distributions

```http
GET /api/v1/distributions?branchId=...&status=DISPATCHED&from=2026-01-01&to=2026-01-31
```

## Get Distribution

```http
GET /api/v1/distributions/{id}
```

## Dispatch

```http
POST /api/v1/distributions/{id}/dispatch
Idempotency-Key: <uuid>
```

## Reverse

```http
POST /api/v1/distributions/{id}/reverse
```

```json
{
  "reason": "Vehicle returned before delivery"
}
```

## Branch Receipt

```http
POST /api/v1/branch-receipts
Idempotency-Key: <uuid>
```

```json
{
  "distributionId": "00000000-0000-0000-0000-000000000000",
  "receivedAt": "2026-01-16T14:00:00Z",
  "items": [
    {
      "distributionItemId": "00000000-0000-0000-0000-000000000000",
      "receivedWeightKg": "147.500",
      "discrepancyReason": "MELT_WATER"
    }
  ]
}
```

## Discrepancy Reasons

- `MELT_WATER`
- `DAMAGE`
- `WEIGHT_DIFFERENCE`
- `SHORT_DELIVERY`
- `OTHER`

## Rules

- Dispatch cannot exceed available stock.
- FIFO may split one item across multiple batches.
- Branch receipt discrepancy requires a reason.
- Dispatch and receipt can be idempotent.
- Reverse after dispatch requires approval.

## Errors

- `INSUFFICIENT_STOCK`
- `INVALID_STATUS_TRANSITION`
- `VALIDATION_FAILED`
- `CONFLICT`
