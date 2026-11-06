# Branch Sales API (day close)

## Status

Implemented.

A branch closes each day by entering **what it took in** (total sales, Rs) and **the stock it has left** of each chicken type. The weight that left the branch is derived: `sold = opening stock - closing stock`. What the branch owes head office is created when it **receives** a delivery, not here (see [Finance API](finance.md)).

## Opening stock for a day

```http
GET /api/v1/branches/{id}/stock?date=2026-01-15
```

Without `date`: the stock the branch holds now. With `date`: the stock before that day closes, i.e. received by the end of the day minus every earlier closed day. The day close form uses it as the opening stock.

```json
{ "data": [ { "chickenTypeId": "33333333-3333-3333-3333-333333333333", "availableWeightKg": "330.000" } ] }
```

## Close a day

```http
POST /api/v1/branch-sales
```

Request:

```json
{
  "branchId": "11111111-1111-1111-1111-111111111111",
  "saleDate": "2026-01-15",
  "totalSales": "295000.00",
  "notes": "Daily counter sales",
  "items": [
    { "chickenTypeId": "33333333-3333-3333-3333-333333333333", "closingWeightKg": "30.000" }
  ]
}
```

Response:

```json
{
  "data": {
    "id": "22222222-2222-2222-2222-222222222222",
    "reference": "SALE-2026-000001",
    "branchId": "11111111-1111-1111-1111-111111111111",
    "saleDate": "2026-01-15",
    "totalSales": "295000.00",
    "expectedSales": "300000.00",
    "variance": "-5000.00",
    "transferValue": "271014.00",
    "branchProfit": "23986.00",
    "notes": "Daily counter sales",
    "items": [
      {
        "id": "44444444-4444-4444-4444-444444444444",
        "chickenTypeId": "33333333-3333-3333-3333-333333333333",
        "openingWeightKg": "330.000",
        "closingWeightKg": "30.000",
        "weightKg": "300.000",
        "sellingPricePerKg": "1000.0000",
        "transferPricePerKg": "903.3800",
        "revenue": "300000.00",
        "cost": "271014.00",
        "profit": "28986.00"
      }
    ],
    "createdAt": "2026-01-15T18:00:00Z",
    "updatedAt": "2026-01-15T18:00:00Z"
  },
  "timestamp": "2026-01-15T18:00:00Z"
}
```

| Field | Meaning |
|---|---|
| `totalSales` | What the branch entered: its takings for the day. |
| `items[].weightKg` | Weight that left the branch: `openingWeightKg - closingWeightKg` (sales plus any waste). |
| `items[].cost`, `transferValue` | Cost of that weight at the transfer price, taken oldest receipt first. This is the branch's **cost of stock sold**, not what it owes. |
| `branchProfit` | `totalSales - transferValue`. |
| `items[].sellingPricePerKg`, `revenue`, `profit` | Branch selling price for that branch, type and date, and the line's expected revenue and profit at it. `null` when no price is set. |
| `expectedSales` | Sum of the lines' `revenue`. `null` when a type that sold has no price. |
| `variance` | `totalSales - expectedSales`: negative means the branch is short. `null` when `expectedSales` is. |

Saving the same branch and date again replaces that day's lines and keeps the same reference.

## List day closes

```http
GET /api/v1/branch-sales?branchId=...&from=2026-01-01&to=2026-01-31&page=0&size=20
```

## Get a day close

```http
GET /api/v1/branch-sales/{id}
```

## Rules

- `totalSales` is required and cannot be negative; `closingWeightKg` cannot be negative.
- Every chicken type the branch holds stock of that day needs a closing count (`VALIDATION_FAILED`); one line per type.
- A closing count cannot be more than the branch held (`CLOSING_ABOVE_STOCK`, 409).
- Only the latest closed day of a branch can be saved or changed: the closing stock of a day is the opening stock of the next (`BUSINESS_RULE_VIOLATION`, 409).
- Stock received after the end of `saleDate` (Asia/Colombo) is not part of that day's opening stock.
- A missing branch selling price no longer blocks the close; expected sales are then unknown.
- Writes need `OWNER`, `BRANCH_RECEIVER` or `ACCOUNTANT`, and the branch must be in the user's scope.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `CLOSING_ABOVE_STOCK`
- `BUSINESS_RULE_VIOLATION`
- `FORBIDDEN`
