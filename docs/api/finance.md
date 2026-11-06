# Finance API

A branch owes head office for every delivery it **receives**: received weight x the line's transfer price. Settlements record what the branch pays and are allocated to those deliveries.

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

Each allocation points at a received delivery (distribution) of the branch and cannot exceed what is still owed on it. The mobile app fills the allocations oldest delivery first.

## Branch Balance

```http
GET /api/v1/branches/{id}/balance
```

Returns `outstandingAmount`: the received value of every received delivery minus posted settlements.

## Open Deliveries

```http
GET /api/v1/branches/{id}/open-deliveries
```

Received deliveries the branch has not fully paid for, oldest first:

```json
{
  "data": [
    {
      "distributionId": "00000000-0000-0000-0000-000000000000",
      "reference": "DST-2026-000041",
      "receivedAt": "2026-11-04T02:30:00Z",
      "owedAmount": "126000.00",
      "outstandingAmount": "26000.00"
    }
  ]
}
```

## Reverse a Settlement

```http
POST /api/v1/branch-settlements/{id}/reverse
```

```json
{ "reason": "Cheque bounced" }
```

## Rules

- Allocations must sum to the settlement amount.
- An allocation cannot exceed the outstanding amount of the delivery it pays (`OVER_ALLOCATION`); allocations to the same delivery are added up first.
- The delivery must belong to the settlement's branch and be received (`RECEIVED` or `CLOSED`).
- Posted settlements are immutable and can only be reversed; a reversal restores the balance.
- A received delivery with posted settlements cannot be reversed until those settlements are reversed.
- Totals are derived from posted allocations.

## Errors

- `OVER_ALLOCATION`
- `BUSINESS_RULE_VIOLATION`
- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `INVALID_STATUS_TRANSITION`
