# Finance API

## Branch Settlement

```http
POST /api/v1/branch-settlements
Idempotency-Key: <uuid>
```

```json
{
  "branchId": "00000000-0000-0000-0000-000000000000",
  "settlementDate": "2026-01-20",
  "amount": "250000.00",
  "method": "CASH",
  "reference": "SETTLE-001",
  "allocations": [
    {
      "distributionId": "00000000-0000-0000-0000-000000000000",
      "amount": "250000.00"
    }
  ]
}
```

## Branch Balance

```http
GET /api/v1/branches/{id}/balance
```

## Rules

- Allocations cannot exceed outstanding balances.
- Posted payments and settlements are immutable.
- Reversals restore balances.
- Totals are derived from posted allocations.

## Errors

- `OVER_ALLOCATION`
- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `CONFLICT`
