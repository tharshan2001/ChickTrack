# Reports API

## Status

Planned.

All report endpoints require a bearer token and support branch scoping.

## Dashboard

```http
GET /api/v1/dashboard?from=2026-01-01&to=2026-01-31&branchId=...
```

Example response:

```json
{
  "procurementValue": "1250000.00",
  "stockWeightKg": "4200.500",
  "stockValue": "3900000.00",
  "distributionWeightKg": "1800.000",
  "branchOutstanding": "450000.00",
  "supplierPayables": "780000.00",
  "shrinkageWeightKg": "12.500"
}
```

## Procurement Report

```http
GET /api/v1/reports/procurement?from=2026-01-01&to=2026-01-31&supplierId=...
```

## Stock Report

```http
GET /api/v1/reports/stock?warehouseId=...&chickenTypeId=...&asOf=2026-01-31
```

## Distribution Report

```http
GET /api/v1/reports/distribution?branchId=...&from=2026-01-01&to=2026-01-31
```

## Profit Report

```http
GET /api/v1/reports/profit?branchId=...&from=2026-01-01&to=2026-01-31
```

## Shrinkage Report

```http
GET /api/v1/reports/shrinkage?from=2026-01-01&to=2026-01-31
```

## Payables Report

```http
GET /api/v1/reports/payables?asOf=2026-01-31
```

## Settlements Report

```http
GET /api/v1/reports/settlements?branchId=...&from=2026-01-01&to=2026-01-31
```

## Export

```http
GET /api/v1/reports/{name}/export?format=csv|pdf&from=...&to=...
```

CSV response:

```http
Content-Type: text/csv
Content-Disposition: attachment; filename="procurement-2026-01.csv"
```

PDF response:

```http
Content-Type: application/pdf
Content-Disposition: attachment; filename="procurement-2026-01.pdf"
```

## Rules

- Reports read posted data only.
- Date ranges and filters are validated.
- Branch-scoped users cannot request another branch.
- Export totals must match the on-screen report.
- Large reports may be generated asynchronously in a future iteration.

## Errors

- `VALIDATION_FAILED`
- `FORBIDDEN`
- `REPORT_GENERATION_FAILED`
