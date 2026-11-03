# Dashboard and Reports

## Status

Implemented.

## Purpose

Provide management visibility into procurement, stock, distribution, profitability, shrinkage, and outstanding branch balances.

## Actors

- Owner
- Accountant
- Managers
- Viewer

## Dashboard

Cards:

- Procurement value and weight for the selected range.
- Current stock weight.
- Stock value.
- Distribution weight and revenue for the selected range.
- Total sales, sales cost, and profit for the selected range.
- Branch outstanding balance.
- Shrinkage and loss.

The dashboard defaults to today. `branchId` scopes distribution, outstanding, and shrinkage figures.

## Reports

Implemented:

- Procurement report.
- Stock by batch report.
- Distribution report.
- Branch sales report.
- Profit and loss report by branch (based on entered branch sales).
- Shrinkage and loss report.
- Branch settlements report.

Supplier purchase and supplier payables reports were dropped with the supplier payments module.

## Filters

- Date range (`from`, `to`).
- Branch (`branchId`).
- Supplier (`supplierId`) on the procurement report.
- Chicken type (`chickenTypeId`) on the stock report.

## Exports

CSV and PDF are generated server-side from the same data that backs the on-screen report.
Excel is deferred.

## Rules

- Reports read posted data only.
- Totals reconcile to document and movement totals.
- Access is role-checked and branch-scoped.
- Report parameters are validated.

## API

Implemented:

- `GET /api/v1/dashboard`
- `GET /api/v1/reports/procurement`
- `GET /api/v1/reports/stock`
- `GET /api/v1/reports/distribution`
- `GET /api/v1/reports/sales`
- `GET /api/v1/reports/profit`
- `GET /api/v1/reports/shrinkage`
- `GET /api/v1/reports/settlements`
- `GET /api/v1/reports/{name}/export?format=csv|pdf`

See [Reports API](../api/reports.md).

## Acceptance Criteria

- Known datasets produce expected totals.
- Exports match on-screen report data.
- Branch-scoped users only see their branch.
- Report queries perform within the agreed threshold on MVP data volume.
