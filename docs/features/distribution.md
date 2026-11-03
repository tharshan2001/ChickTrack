# Distribution and Branch Receiving

## Status

Planned.

## Purpose

Move stock to branches, record delivery, and capture discrepancies.

## Actors

- Distribution Manager
- Driver
- Branch Receiver
- Owner

## Flow

1. Create a distribution for a branch.
2. Add chicken types and requested weights.
3. Backend allocates stock using FIFO.
4. Selling prices are resolved and snapshotted.
5. Distribution is dispatched.
6. Branch receives the delivery.
7. Branch records received weight and discrepancy reasons.
8. Differences update inventory and cost reporting.
9. Posted distributions can only be reversed.

## Rules

- Distribution cannot exceed available stock.
- A distribution line may split across multiple batches.
- Each line stores the allocated batch cost and selling price.
- Branch receiving requires a reason for any discrepancy.
- Missing or damaged stock is tracked separately.
- Dispatch and receiving actions are audited.
- Notifications are emitted for dispatch and receipt once implemented.

## Status Flow

```text
DRAFT -> DISPATCHED -> RECEIVED -> CLOSED
```

Cancellation after dispatch requires an approved reversal.

## Data

Planned tables:

- `distributions`
- `distribution_items`
- `distribution_expenses`
- `expense_allocations`
- `branch_receipts`

## API

Planned:

- `POST /api/v1/distributions`
- `GET /api/v1/distributions`
- `GET /api/v1/distributions/{id}`
- `POST /api/v1/distributions/{id}/dispatch`
- `POST /api/v1/distributions/{id}/reverse`
- `POST /api/v1/branch-receipts`
- `GET /api/v1/branch-receipts`

See [Distribution API](../api/distribution.md).

## Acceptance Criteria

- Over-stock distribution is rejected.
- FIFO splits are recorded per batch.
- Branch receiving discrepancy requires a reason.
- Stock after receiving matches batch movements.
- Reversal restores stock and preserves the original records.
