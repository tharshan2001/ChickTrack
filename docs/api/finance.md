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
      "branchSaleId": "00000000-0000-0000-0000-000000000000",
      "amount": "250000.00"
    }
  ]
}
```

Settlements settle the daily sales the branch entered. Each allocation points at a branch sale and cannot exceed that sale's outstanding amount.

## Branch Balance

```http
GET /api/v1/branches/{id}/balance
```

Returns `outstandingAmount`, which is the transfer value of entered sales minus posted settlements.

## Rules

- Allocations must sum to the settlement amount.
- An allocation cannot exceed the transfer value of the branch sale it settles.
- The branch sale must belong to the settlement's branch.
- Posted settlements are immutable and can only be reversed.
- Reversals restore balances.
- Totals are derived from posted allocations.

## Errors

- `OVER_ALLOCATION`
- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `CONFLICT`
