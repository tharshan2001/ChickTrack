# Dashboard and Reports

## Status

Planned.

## Purpose

Provide management visibility into procurement, stock, distribution, profitability, and outstanding balances.

## Actors

- Owner
- Accountant
- Managers

## Dashboard

Cards:

- Today's procurement value.
- Current stock weight.
- Stock value.
- Today's distributions.
- Branch outstanding balance.
- Supplier payables.
- Shrinkage and loss.

Charts:

- Purchase price trend.
- Distribution by branch.
- Stock ageing.
- Profit by branch.

## Reports

- Procurement report.
- Supplier purchase report.
- Procurement cost and landed-cost report.
- Purchase price report.
- Stock by batch report.
- Stock movement report.
- Batch ageing report.
- Distribution report.
- Branch summary report.
- Profit and margin report.
- Shrinkage and loss report.
- Expense report.
- Supplier payables report.
- Branch settlements report.
- Consolidated management report.

## Filters

- Date range.
- Branch.
- Supplier.
- Chicken type.
- Batch.
- Status.

## Exports

MVP: CSV and PDF. Excel is deferred.

## Rules

- Reports read posted data only.
- Totals reconcile to document and movement totals.
- Large exports are generated server-side.
- Access is role-checked and branch-scoped.
- Report parameters are validated.

## API

Planned:

- `GET /api/v1/dashboard`
- `GET /api/v1/reports/procurement`
- `GET /api/v1/reports/stock`
- `GET /api/v1/reports/distribution`
- `GET /api/v1/reports/profit`
- `GET /api/v1/reports/shrinkage`
- `GET /api/v1/reports/payables`
- `GET /api/v1/reports/settlements`
- `GET /api/v1/reports/{name}/export?format=csv|pdf`

See [Reports API](../api/reports.md).

## Acceptance Criteria

- Known datasets produce expected totals.
- Exports match on-screen report data.
- Branch-scoped users only see their branch.
- Report queries perform within the agreed threshold on MVP data volume.
