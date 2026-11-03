# Finance API

## Status

Planned.

## Supplier Payment

```http
POST /api/v1/payments
Authorization: Bearer <token>
Idempotency-Key: <uuid>
```

```json
{
  "supplierId": "00000000-0000-0000-0000-000000000000",
  "paymentDate": "2026-01-20",
  "amount": "500000.00",
  "method": "BANK_TRANSFER",
  "reference": "BANK-12345",
  "allocations": [
    {
      "procurementId": "00000000-0000-0000-0000-000000000000",
      "amount": "500000.00"
    }
  ]
}
```

## List Payments

```http
GET /api/v1/payments?supplierId=...&from=2026-01-01&to=2026-01-31
```

## Reverse Payment

```http
POST /api/v1/payments/{id}/reverse
```

```json
{
  "reason": "Payment returned by bank"
}
```

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

## Supplier Balance

```http
GET /api/v1/suppliers/{id}/balance
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
