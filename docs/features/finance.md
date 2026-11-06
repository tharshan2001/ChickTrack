# Branch Settlements

## Status

Implemented.

## Purpose

Track the money a branch pays for the stock it received, so outstanding branch balances are visible.

## Actors

- Accountant
- Owner

## Rules

- A branch owes us when it receives a delivery: received weight x transfer price of each line.
- Settlements are allocated against the received deliveries they pay for, oldest first in the app.
- An allocation cannot exceed what is still owed on that delivery.
- Allocations must sum to the settlement amount.
- The delivery must belong to the settlement's branch and be received.
- A delivery with payments against it cannot be reversed until the payments are reversed.
- Settlements are immutable once posted; corrections use reversal records.
- Overpayment is rejected.
- Balances are calculated from posted allocations: received value of deliveries minus posted settlements.

## Branch Settlements

- Branch
- Settlement date
- Amount
- Method
- Reference
- Allocations to received deliveries

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
- `GET /api/v1/branches/{id}/open-deliveries`

See [Finance API](../api/finance.md).

## Acceptance Criteria

- Outstanding balances match received deliveries minus posted settlements.
- Over-allocation is rejected.
- Reversals restore balances.
