# Branch Settlements

## Status

Implemented.

## Purpose

Track the money a branch pays back for the sales it entered, so outstanding branch balances are visible.

## Actors

- Accountant
- Owner

## Rules

- Settlements are allocated against the daily branch sales they pay for.
- An allocation cannot exceed the outstanding amount of that branch sale.
- Allocations must sum to the settlement amount.
- The branch sale must belong to the settlement's branch.
- Settlements are immutable once posted; corrections use reversal records.
- Overpayment is rejected.
- Balances are calculated from posted allocations: entered sales minus posted settlements.

## Branch Settlements

- Branch
- Settlement date
- Amount
- Method
- Reference
- Allocations to branch sales

## Data

- `branch_settlements`
- `branch_settlement_allocations`

## API

Implemented:

- `POST /api/v1/branch-settlements`
- `GET /api/v1/branch-settlements`
- `GET /api/v1/branch-settlements/{id}`
- `POST /api/v1/branch-settlements/{id}/reverse`
- `GET /api/v1/branches/{id}/balance`

See [Finance API](../api/finance.md).

## Acceptance Criteria

- Outstanding balances match entered sales minus posted settlements.
- Over-allocation is rejected.
- Reversals restore balances.
