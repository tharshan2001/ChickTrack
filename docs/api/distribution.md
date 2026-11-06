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
      "weightKg": "150.000"
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

## Transfer Price

The backend prices every line and the branch is invoiced the transfer value:

```text
landed cost (from the batch)
+ our margin (landed cost x company markup percent)
+ distribution expenses allocated per kg
= transfer price per kg
```

The branch selling price is not part of a distribution; it gives the expected sales of a day close.

### Splitting distribution expenses

Each expense in `expenses` has an `allocationMethod`:

- `BY_WEIGHT` (default): the amount is shared across all lines by weight.
- `MANUAL`: `manualShares` gives the amount each chicken type carries. The amounts must add up to the expense, there is one per chicken type, and each must be a type on the dispatch (`VALIDATION_FAILED` otherwise). A type's amount is shared across its lines (one per stock batch) by weight. Other methods must not send `manualShares`.

```json
{
  "categoryId": "…",
  "description": "Hired lorry",
  "amount": "3000.00",
  "allocationMethod": "MANUAL",
  "manualShares": [
    { "chickenTypeId": "<broiler>", "amount": "1800.00" },
    { "chickenTypeId": "<country>", "amount": "1200.00" }
  ]
}
```

Either way the allocated amount per kg is added to the line's transfer price, so the branch pays it.

## Rules

- Dispatch cannot exceed available stock.
- The branch selling price is not entered on a distribution.
- FIFO may split one item across multiple batches.
- Branch receipt discrepancy requires a reason.
- Dispatch and receipt can be idempotent.
- Reverse after dispatch requires approval.

## Errors

- `INSUFFICIENT_STOCK`
- `INVALID_STATUS_TRANSITION`
- `VALIDATION_FAILED`
- `CONFLICT`
