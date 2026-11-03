# Inventory and Batches

## Status

Planned.

## Purpose

Track received stock by batch, cost, weight, and movement history.

## Actors

- Warehouse Manager
- Distribution Manager
- Owner

## Rules

- Every receipt creates a batch.
- `inventory_movements` is the source of truth.
- `stock_batches.remaining_weight` is updated transactionally.
- Remaining weight cannot be negative.
- Adjustments require a reason and approval.
- Damage, loss, and returns are recorded as movements.
- FIFO allocation locks batches before allocating weight.
- The final issue consumes the batch's remaining cost to zero.

## Movement Types

- `RECEIPT`
- `DISTRIBUTION`
- `RETURN`
- `DAMAGE`
- `LOSS`
- `ADJUSTMENT`
- `REVERSAL`

## Data

Planned tables:

- `stock_batches`
- `inventory_movements`
- `stock_adjustments`

## FIFO

1. Select batches for the chicken type ordered by received date.
2. Lock candidate rows with `SELECT ... FOR UPDATE`.
3. Allocate available weight until the requested quantity is satisfied.
4. Create distribution line splits per batch.
5. Reject the request if total available weight is insufficient.
6. Audit any override.

## API

Planned:

- `GET /api/v1/stock-batches`
- `GET /api/v1/stock-batches/{id}`
- `GET /api/v1/inventory-movements`
- `POST /api/v1/stock-adjustments`

See [Inventory API](../api/inventory.md).

## Acceptance Criteria

- Stock cannot go negative under concurrent dispatch.
- Movement ledger reconciles to batch balances.
- Batch cost reconciles to zero after full issue.
- Ageing report matches batch data.
