# Procurement API

## Status

Planned.

## Create Procurement

```http
POST /api/v1/procurements
Authorization: Bearer <token>
Idempotency-Key: <uuid>
```

```json
{
  "supplierId": "00000000-0000-0000-0000-000000000000",
  "procurementDate": "2026-01-15",
  "items": [
    {
      "chickenTypeId": "00000000-0000-0000-0000-000000000000",
      "paidWeightKg": "1000.000",
      "purchasePricePerKg": "830.0000",
      "commissionMethod": "PERCENTAGE",
      "commissionValue": "2.0000"
    }
  ],
  "expenses": [
    {
      "categoryId": "00000000-0000-0000-0000-000000000000",
      "description": "Transport",
      "amount": "9000.00"
    }
  ]
}
```

## List Procurements

```http
GET /api/v1/procurements?supplierId=...&status=RECEIVED&from=2026-01-01&to=2026-01-31
```

## Get Procurement

```http
GET /api/v1/procurements/{id}
```

## Approve

```http
POST /api/v1/procurements/{id}/approve
```

## Receive

```http
POST /api/v1/procurements/{id}/receipts
Idempotency-Key: <uuid>
```

```json
{
  "receivedAt": "2026-01-15T10:00:00Z",
  "items": [
    {
      "procurementItemId": "00000000-0000-0000-0000-000000000000",
      "receivedWeightKg": "990.000"
    }
  ]
}
```

## Add Expense

```http
POST /api/v1/procurements/{id}/expenses
```

## Reverse

```http
POST /api/v1/procurements/{id}/reverse
```

```json
{
  "reason": "Incorrect supplier invoice"
}
```

## Response

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "reference": "PRC-2026-000123",
  "status": "PARTIALLY_RECEIVED",
  "purchaseValue": "830000.00",
  "totalCost": "838000.00",
  "receivedWeightKg": "990.000",
  "landedCostPerKg": "838.3838"
}
```

## Rules

- Landed cost uses received weight.
- Partial receipts are allowed until the approved quantity is reached.
- Posted records are reversed, not edited.
- Duplicate idempotency keys return the original result.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `BUSINESS_RULE_VIOLATION`
- `CONFLICT`
