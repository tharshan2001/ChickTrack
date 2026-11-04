# Inventory API

## Status

Implemented.

Read: `OWNER`, `WAREHOUSE_MANAGER`, `DISTRIBUTION_MANAGER`, `PROCUREMENT_MANAGER`, `ACCOUNTANT`.
Adjust: `OWNER`, `WAREHOUSE_MANAGER`.

## List Stock Batches

```http
GET /api/v1/stock-batches?chickenTypeId=...&available=true&page=0&size=20
```

`available=true` returns batches with remaining weight, `false` returns empty batches.

```json
{
  "data": {
    "content": [
      {
        "id": "00000000-0000-0000-0000-000000000000",
        "chickenTypeId": "00000000-0000-0000-0000-000000000000",
        "chickenTypeName": "Broiler",
        "procurementItemId": "00000000-0000-0000-0000-000000000000",
        "receivedWeightKg": "990.000",
        "remainingWeightKg": "600.000",
        "landedCostPerKg": "838.3838",
        "remainingValue": "503030.28",
        "receivedAt": "2026-01-15T10:00:00Z",
        "ageDays": 12
      }
    ],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  }
}
```

## Get Batch

```http
GET /api/v1/stock-batches/{id}
```

## List Movements

```http
GET /api/v1/inventory-movements?batchId=...&type=DISTRIBUTION&from=2026-01-01&to=2026-01-31
```

Dates are business days in `Asia/Colombo`, both inclusive. Newest first.

## Stock Adjustments

```http
GET  /api/v1/stock-adjustments?batchId=...
POST /api/v1/stock-adjustments
Idempotency-Key: <uuid>
```

```json
{
  "stockBatchId": "00000000-0000-0000-0000-000000000000",
  "type": "DAMAGE",
  "weightKg": "5.000",
  "reason": "Crate damage during handling"
}
```

| Type | Effect on remaining weight |
|---|---|
| `DAMAGE` | decreases |
| `LOSS` | decreases |
| `RETURN` | increases |
| `ADJUSTMENT` | increases or decreases; send `"direction": "INCREASE"` or `"DECREASE"` |

Response:

```json
{
  "data": {
    "id": "...",
    "reference": "ADJ-2026-000001",
    "stockBatchId": "...",
    "type": "DAMAGE",
    "weightChangeKg": "-5.000",
    "remainingWeightKg": "595.000",
    "reason": "Crate damage during handling",
    "createdAt": "2026-11-04T10:00:00Z"
  }
}
```

## Rules

- The batch is locked while it is adjusted.
- Remaining weight cannot go below zero (`INSUFFICIENT_STOCK`) or above the batch's received weight (`BUSINESS_RULE_VIOLATION`).
- Every adjustment writes a `stock_adjustments` row and a matching `inventory_movements` row with `referenceType = STOCK_ADJUSTMENT`. Both are append-only; correct a mistake with an opposite adjustment.
- A reason is required. Adjustments are audited.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `INSUFFICIENT_STOCK`
- `BUSINESS_RULE_VIOLATION`
- `FORBIDDEN`
