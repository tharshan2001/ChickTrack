# Procurement and Receiving

## Status

Planned.

## Purpose

Record supplier purchases, commissions, expenses, and actual received weights.

## Actors

- Procurement Manager
- Warehouse Manager
- Owner

## Flow

1. Create a procurement with supplier, chicken type, paid weight, purchase price, and expenses.
2. Optional commission is calculated using the configured method.
3. A stock batch is created automatically for each procurement item.
6. Landed cost is calculated using received weight.
7. Posted records can only be corrected through reversal documents.

## Commission Methods

- Fixed amount.
- Per kg.
- Percentage of purchase value.
- Per bird.

## Cost Rules

- Purchase value = paid weight x purchase price.
- Total procurement cost = purchase value + commission + procurement expenses.
- Landed cost per kg = total procurement cost / received weight.
- Received weight can differ from paid weight; shrinkage is tracked.
- Expenses must sum exactly when allocated.

## Example

```text
Purchase value: 830,000
Received weight: 990 kg
Landed cost: 830,000 / 990 = 838.3838 per kg
```

## Status

Procurements are created as `RECEIVED` and are not edited after creation. Corrections use reversal documents.

## Data

Planned tables:

- `procurements`
- `procurement_items`
- `procurement_receipts`
- `procurement_expenses`

## API

Planned:

- `POST /api/v1/procurements`
- `GET /api/v1/procurements`
- `GET /api/v1/procurements/{id}`

See [Procurement API](../api/procurement.md).

## Acceptance Criteria

- Procurement creates stock batches automatically.
- Landed cost uses received weight.
- Commission methods produce expected totals.
