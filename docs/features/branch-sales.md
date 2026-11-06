# Branch Sales (day close)

## Status

Implemented.

## Purpose

Capture what a branch took in each day and the stock it has left, derive what it sold, and mark branch profit against the transfer price and short/over against the branch price.

## Actors

- Branch Receiver
- Accountant
- Owner

## Rules

- A branch closes each day once: it enters its total takings (Rs) and counts the stock left of every chicken type it holds.
- Sold weight per type = opening stock - closing stock. Opening stock = received by the end of the day - earlier closed days.
- Cost of the sold weight is taken at the transfer price, oldest receipt first; branch profit = takings - that cost.
- Expected sales = sold weight x the branch selling price for that branch, type and date; short/over = takings - expected. Without a price, expected is unknown and the close still saves.
- A closing count above what the branch held is refused; every type with stock needs a count.
- Saving the same day again replaces it and keeps the reference. Only the latest closed day can change, because its closing stock is the next day's opening stock.
- The day close does not create what the branch owes: that is created when it receives a delivery (see [Branch Settlements](finance.md)).

## Data

- `branch_sales`
- `branch_sale_items`

## API

Implemented:

- `POST /api/v1/branch-sales`
- `GET /api/v1/branch-sales`
- `GET /api/v1/branch-sales/{id}`
- `GET /api/v1/branches/{id}/stock?date=` (opening stock of the day)

See [Branch Sales API](../api/branch-sales.md).

## Acceptance Criteria

- One day close per branch per day, with one line per chicken type held.
- Sold weight equals opening minus closing stock; closing above opening is rejected.
- Branch profit equals takings minus the cost of the sold weight.
- Short/over equals takings minus expected sales at the branch price.
- Re-closing a day updates the entry instead of duplicating it; an earlier day cannot change once a later day is closed.
