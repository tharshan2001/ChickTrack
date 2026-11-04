# Branch Sales

## Status

Implemented.

## Purpose

Capture what a branch actually sold, at the selling price we set for that branch, and mark branch profit against the transfer price.

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
- Branch settlements settle these sales: outstanding balance = entered sales - posted settlements.

## Data

- `branch_sales`
- `branch_sale_items`

## API

Implemented:

- `POST /api/v1/branch-sales`
- `GET /api/v1/branch-sales`
- `GET /api/v1/branch-sales/{id}`

See [Branch Sales API](../api/branch-sales.md).

## Acceptance Criteria

- One sales entry per branch per day, with one line per chicken type.
- Branch profit equals sales revenue minus transfer value.
- Selling more than the branch holds is rejected.
- Re-recording a day updates the entry instead of duplicating it.
