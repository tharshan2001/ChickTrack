# Branch Sales API

## Status

Implemented.

## Record Sales

```http
POST /api/v1/branch-sales
```

Request:

```json
{
  "branchId": "11111111-1111-1111-1111-111111111111",
  "saleDate": "2026-01-15",
  "notes": "Daily counter sales",
  "items": [
    {
      "chickenTypeId": "33333333-3333-3333-3333-333333333333",
      "weightKg": "300.000"
    }
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
    "totalSales": "300000.00",
    "transferValue": "271014.00",
    "branchProfit": "28986.00",
    "notes": "Daily counter sales",
    "items": [
      {
        "id": "44444444-4444-4444-4444-444444444444",
        "chickenTypeId": "33333333-3333-3333-3333-333333333333",
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

- `sellingPricePerKg` is the branch selling price from `daily_prices` for the branch, chicken type, and date.
- `transferPricePerKg` is the average transfer price of the stock the branch received for that chicken type.
- `transferValue` is what the branch owes us; `branchProfit` is what the branch earns.

Recording the same branch and date again updates the existing entry and returns the same reference.

## List Sales

```http
GET /api/v1/branch-sales?branchId=...&from=2026-01-01&to=2026-01-31&page=0&size=20
```

## Get Sale

```http
GET /api/v1/branch-sales/{id}
```

## Rules

- `weightKg` must be greater than zero.
- A branch selling price must exist for the chicken type and date (`PRICE_NOT_CONFIGURED`).
- The sold weight cannot exceed the stock the branch received (`INSUFFICIENT_BRANCH_STOCK`).
- `saleDate` is required; branch and chicken type must exist.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
- `PRICE_NOT_CONFIGURED`
- `INSUFFICIENT_BRANCH_STOCK`
