# Reports API

## Status

Implemented.

All report endpoints require a bearer token, are role-checked, and honour branch scoping.

Access roles: `OWNER`, `ACCOUNTANT`, `VIEWER`, `PROCUREMENT_MANAGER`, `WAREHOUSE_MANAGER`, `DISTRIBUTION_MANAGER`.

## Dashboard

```http
GET /api/v1/dashboard?from=2026-01-01&to=2026-01-31&branchId=...
```

`from` and `to` default to today. `branchId` is optional and scopes distribution, outstanding, and shrinkage figures.

Example response:

```json
{
  "data": {
    "from": "2026-01-01",
    "to": "2026-01-31",
    "procurementValue": "1250000.00",
    "procurementWeightKg": "1500.000",
    "stockWeightKg": "4200.500",
    "stockValue": "3900000.00",
    "distributionWeightKg": "1800.000",
    "distributionRevenue": "1620000.00",
    "branchOutstanding": "450000.00",
    "shrinkageWeightKg": "12.500"
  },
  "timestamp": "2026-01-31T10:00:00Z"
}
```

## Report Endpoints

All report endpoints return the same envelope:

```json
{
  "data": {
    "name": "procurement",
    "rows": [],
    "totals": {}
  },
  "timestamp": "2026-01-31T10:00:00Z"
}
```

### Procurement

```http
GET /api/v1/reports/procurement?from=2026-01-01&to=2026-01-31&supplierId=...
```

Totals: `purchaseValue`, `commissionAmount`, `expenseAmount`, `totalCost`, `paidWeightKg`, `landedCostPerKg`.

### Stock

```http
GET /api/v1/reports/stock?chickenTypeId=...&asOf=2026-01-31
```

Totals: `receivedWeightKg`, `remainingWeightKg`, `stockValue`.

### Distribution

```http
GET /api/v1/reports/distribution?branchId=...&from=2026-01-01&to=2026-01-31
```

Totals: `weightKg`, `cost`, `revenue`, `margin`, `expenseAmount`.

### Profit

```http
GET /api/v1/reports/profit?branchId=...&from=2026-01-01&to=2026-01-31
```

Rows are grouped per branch. Totals: `distributionCount`, `weightKg`, `revenue`, `cost`, `expenseAmount`, `margin`, `marginPercent`.

### Shrinkage

```http
GET /api/v1/reports/shrinkage?branchId=...&from=2026-01-01&to=2026-01-31
```

Totals: `shrinkageWeightKg`.

### Settlements

```http
GET /api/v1/reports/settlements?branchId=...&from=2026-01-01&to=2026-01-31
```

Totals: `settledAmount`.

## Export

```http
GET /api/v1/reports/{name}/export?format=csv|pdf&from=...&to=...&branchId=...&supplierId=...&chickenTypeId=...
```

`{name}` is one of `procurement`, `stock`, `distribution`, `profit`, `shrinkage`, `settlements`.

CSV response:

```http
Content-Type: text/csv
Content-Disposition: attachment; filename="procurement-2026-01-01-to-2026-01-31.csv"
```

PDF response:

```http
Content-Type: application/pdf
Content-Disposition: attachment; filename="procurement-2026-01-01-to-2026-01-31.pdf"
```

Exports contain the same rows and totals that the on-screen report returns.

## Rules

- Reports read posted data only: draft and reversed procurements and distributions are excluded, and reversed settlements are excluded.
- Date ranges and filters are validated; `from` must not be after `to`.
- Branch-scoped users cannot request a branch outside their assigned branches (`FORBIDDEN`).
- Export totals match the on-screen report.

## Errors

- `VALIDATION_FAILED`
- `FORBIDDEN`
- `REPORT_NOT_FOUND`
- `REPORT_GENERATION_FAILED`
