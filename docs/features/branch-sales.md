# Branch Sales

## Status

Implemented.

## Purpose

Capture what a branch actually sold so profit and loss can be marked without FIFO costing at the branch.

## Actors

- Branch Receiver
- Accountant
- Owner

## Rules

- A branch enters one total sales amount per branch per day.
- No quantity, weight, or chicken type is captured at the branch, so no FIFO costing happens at sale time.
- Recording sales again for the same branch and date replaces the earlier figure and keeps the same reference.
- Cost is taken from the stock the branch received on that date: received weight multiplied by the landed cost assigned at dispatch.
- Profit or loss is `total sales - cost`.
- Cost is captured when the day's sales are recorded; receipts recorded later do not retroactively change it.
- Branch settlement balances are still based on dispatched value and are not yet aligned to entered sales.

## Data

- `branch_sales`

## API

Implemented:

- `POST /api/v1/branch-sales`
- `GET /api/v1/branch-sales`
- `GET /api/v1/branch-sales/{id}`

See [Branch Sales API](../api/branch-sales.md).

## Acceptance Criteria

- One sales figure per branch per day.
- Profit equals total sales minus the cost of stock received that day.
- Re-recording a day updates the figure instead of duplicating it.
