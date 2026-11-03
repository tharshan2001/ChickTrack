# Payments and Settlements

## Status

Planned.

## Purpose

Track supplier payments and branch settlements so outstanding balances are visible.

## Actors

- Accountant
- Owner
- Procurement Manager

## Rules

- Payments are allocated against procurement documents.
- Settlements are allocated against distribution documents.
- An allocation cannot exceed the outstanding balance.
- Payments and settlements are immutable once posted.
- Corrections use reversal records.
- Overpayment is rejected unless explicitly configured.
- Balances are calculated from posted allocations, not editable fields.

## Supplier Payments

- Supplier
- Payment date
- Amount
- Method
- Reference
- Allocations to procurements

## Branch Settlements

- Branch
- Settlement date
- Amount
- Method
- Reference
- Allocations to distributions

## Data

Planned tables:

- `payments`
- `payment_allocations`
- `branch_settlements`

## API

Planned:

- `POST /api/v1/payments`
- `GET /api/v1/payments`
- `POST /api/v1/payments/{id}/reverse`
- `POST /api/v1/branch-settlements`
- `GET /api/v1/branch-settlements`
- `GET /api/v1/suppliers/{id}/balance`
- `GET /api/v1/branches/{id}/balance`

See [Finance API](../api/finance.md).

## Acceptance Criteria

- Outstanding balances match allocation totals.
- Over-allocation is rejected.
- Reversals restore balances.
- Payments and settlements are audited.
