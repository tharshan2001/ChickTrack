# Inventory API

## Status

Planned.

## List Stock Batches

```http
GET /api/v1/stock-batches?chickenTypeId=...&active=true&minRemainingWeight=1
```

Example response:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "batchReference": "BAT-2026-000123",
      "chickenTypeId": "00000000-0000-0000-0000-000000000000",
      "receivedWeightKg": "990.000",
      "remainingWeightKg": "600.000",
      "landedCostPerKg": "838.3838",
      "receivedAt": "2026-01-15T10:00:00Z",
      "ageDays": 12
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
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

## Create Adjustment

```http
POST /api/v1/stock-adjustments
Idempotency-Key: <uuid>
```

```json
{
  "batchId": "00000000-0000-0000-0000-000000000000",
  "type": "DAMAGE",
  "weightKg": "5.000",
  "reason": "Crate damage during handling"
}
```

## Movement Types

`RECEIPT`, `DISTRIBUTION`, `RETURN`, `DAMAGE`, `LOSS`, `ADJUSTMENT`, `REVERSAL`

## Rules

- Remaining weight cannot be negative.
- Adjustments require a reason.
- All movements are append-only.
- Batches are locked during allocation.
- Final issue consumes remaining batch cost.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `INSUFFICIENT_STOCK`
- `CONFLICT`
