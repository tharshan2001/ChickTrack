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

- A branch enters one sales entry per day, with the weight sold per chicken type.
- Revenue uses the branch selling price for that branch, chicken type and date.
- The transfer value (what the branch owes us) costs the sold weight against the stock the branch still holds, oldest receipt first.
- Recording sales again for the same branch and date replaces that day's lines and keeps the same reference; it is refused if it would drop below the amount already settled for that day.
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
