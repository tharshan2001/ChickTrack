# Daily Prices

## Status

Planned.

## Purpose

Record effective-dated purchase and selling prices so procurement and distribution calculations are reproducible.

## Actors

- Procurement Manager
- Owner

## Rules

- Prices are effective from a date and time.
- A correction creates a new price record.
- Historical records are never edited.
- Procurement stores a snapshot of the price used.
- Distribution stores a snapshot of the selling price used.
- A price may apply to all suppliers or a specific supplier.
- A price may apply to all branches or a specific branch.
- Prices are scoped by chicken type and branch.

## Data

`daily_prices` (planned):

- Chicken type
- Optional branch
- Optional supplier
- Purchase price per kg
- Selling price per kg
- Effective from
- Created by
- Created at

## API

Planned:

- `POST /api/v1/daily-prices`
- `GET /api/v1/daily-prices`
- `GET /api/v1/daily-prices/current`
- `GET /api/v1/daily-prices/trend`

See [Pricing API](../api/pricing.md).

## Acceptance Criteria

- The current price is resolved by date and optional supplier.
- Changing a price does not alter existing procurement or distribution records.
- Price history is visible and ordered.
