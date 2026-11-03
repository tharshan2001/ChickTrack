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
3. Procurement is approved.
4. Stock is received in one or more receipts.
5. A stock batch is created for each received item.
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

## Status Flow

```text
DRAFT -> APPROVED -> PARTIALLY_RECEIVED -> RECEIVED -> CLOSED
```

A posted procurement can be reversed, not edited.

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
- `POST /api/v1/procurements/{id}/approve`
- `POST /api/v1/procurements/{id}/receipts`
- `POST /api/v1/procurements/{id}/expenses`
- `POST /api/v1/procurements/{id}/reverse`

See [Procurement API](../api/procurement.md).

## Acceptance Criteria

- Partial receipts update status correctly.
- Landed cost uses received weight.
- Commission methods produce expected totals.
- Reversal preserves the original document.
- Concurrent operations cannot over-receive an approved quantity.
