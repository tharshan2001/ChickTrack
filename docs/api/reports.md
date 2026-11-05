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
    "distributionTransferValue": "1620000.00",
    "ourMargin": "97000.00",
    "totalSales": "1500000.00",
    "branchProfit": "320000.00",
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

Totals: `weightKg`, `cost` (landed), `transferValue`, `ourMargin`, `expenseAmount`.

### Sales

```http
GET /api/v1/reports/sales?branchId=...&from=2026-01-01&to=2026-01-31
```

Totals: `totalSales`, `transferValue`, `branchProfit`, `profitPercent`.

### Profit

```http
GET /api/v1/reports/profit?branchId=...&from=2026-01-01&to=2026-01-31
```

Rows are grouped per branch and are based on the sales the branch entered.
Totals: `saleCount`, `totalSales`, `transferValue`, `branchProfit`, `profitPercent`.

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

## Profit and Loss

Access: `OWNER`, `ACCOUNTANT`.

```http
GET /api/v1/reports/profit-loss?period=daily&date=2026-11-05
GET /api/v1/reports/profit-loss?period=monthly&month=2026-11        (or ?month=2026-11)
GET /api/v1/reports/profit-loss?period=yearly&year=2026             (or ?year=2026)
GET /api/v1/reports/profit-loss?period=custom&from=2026-08-01&to=2026-10-31&groupBy=month
GET /api/v1/reports/profit-loss/export?format=csv|pdf&...same parameters
```

| Parameter | Meaning |
|---|---|
| `period` | `daily`, `monthly`, `yearly` or `custom`. Default `monthly`, or inferred from `month`, `year` or `from`/`to` |
| `date` | Any day in the wanted day, month or year (default today) |
| `month` | `yyyy-MM`, for monthly |
| `year` | For yearly |
| `from`, `to` | Required for custom (10 years at most) |
| `groupBy` | `none`, `day`, `month` or `year`. Default: none for daily and custom, `day` for monthly, `month` for yearly. At most 400 rows |
| `branchId` | One branch only: leaves out warehouse losses and head-office expenses |

How each line is worked out:

| Line | Source | Dated by |
|---|---|---|
| `headOffice.transferRevenue` | Transfer value of posted distributions (what branches are charged) | distribution date |
| `headOffice.costOfGoods` | Landed cost of that stock (purchase + commission + procurement expenses) | distribution date |
| `headOffice.distributionExpenses` | Distribution expenses, passed through in the transfer price | distribution date |
| `headOffice.margin` | Our margin: transfer revenue − cost of goods − distribution expenses | distribution date |
| `branches.sales` / `costOfSales` / `profit` | Branch sales, the transfer value of the stock sold, and their difference | sale date |
| `stockLosses.warehouseValue` | Warehouse loss and damage at the batch landed cost | day recorded |
| `stockLosses.transitValue` | Receiving discrepancies at the line's transfer price (the branch is not charged for them) | receipt `receivedAt` |
| `operatingExpenses` | Posted [operating expenses](expenses.md), total and per category | expense date |
| `netProfit` | `headOffice.margin + branches.profit − stockLosses.value − operatingExpenses.total` | |
| `netMarginPercent` | `netProfit / branches.sales × 100` (null without sales) | |

Our margin is counted when stock is dispatched, so stock a branch still holds at the end of the period already carries it. Procurement commission and expenses are inside the landed cost and are not subtracted again.

Example (`?month=2026-11`, breakdown shortened):

```json
{
  "data": {
    "period": "MONTHLY",
    "from": "2026-11-01",
    "to": "2026-11-30",
    "groupBy": "DAY",
    "branchId": null,
    "summary": {
      "headOffice": { "transferRevenue": "1620000.00", "costOfGoods": "1480000.00", "distributionExpenses": "43000.00", "margin": "97000.00" },
      "branches": { "sales": "1500000.00", "costOfSales": "1180000.00", "profit": "320000.00" },
      "stockLosses": { "weightKg": "14.500", "warehouseValue": "4000.00", "transitValue": "8000.00", "value": "12000.00" },
      "operatingExpenses": {
        "total": "185000.00",
        "byCategory": [
          { "categoryId": "…", "categoryName": "Salaries", "amount": "120000.00" },
          { "categoryId": "…", "categoryName": "Rent", "amount": "65000.00" }
        ]
      },
      "netProfit": "220000.00",
      "netMarginPercent": "14.67"
    },
    "breakdown": [
      { "label": "2026-11-01", "from": "2026-11-01", "to": "2026-11-01", "statement": { "…": "same shape as summary" } }
    ]
  },
  "timestamp": "2026-11-30T18:00:00Z"
}
```

The export has one row per breakdown period (or one row for the whole period) and the summary, including each expense category, as totals.

## Export

```http
GET /api/v1/reports/{name}/export?format=csv|pdf&from=...&to=...&branchId=...&supplierId=...&chickenTypeId=...
```

`{name}` is one of `procurement`, `stock`, `distribution`, `sales`, `profit`, `shrinkage`, `settlements`.

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
