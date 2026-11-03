# Branch Settlements

## Status

Planned.

## Purpose

Track branch settlements so outstanding branch balances are visible.

## Actors

- Accountant
- Owner
- Procurement Manager

## Rules

- Settlements are allocated against distribution documents.
- An allocation cannot exceed the outstanding balance.
- Settlements are immutable once posted.
- Corrections use reversal records.
- Overpayment is rejected unless explicitly configured.
- Balances are calculated from posted allocations, not editable fields.


## Branch Settlements

- Branch
- Settlement date
- Amount
- Method
- Reference
- Allocations to distributions

## Data

Planned tables:

- `branch_settlements`

## API

Planned:

- `POST /api/v1/branch-settlements`
- `GET /api/v1/branch-settlements`
- `GET /api/v1/branches/{id}/balance`

See [Finance API](../api/finance.md).

## Acceptance Criteria

- Outstanding balances match allocation totals.
- Over-allocation is rejected.
- Reversals restore balances.
- Settlements are audited.
