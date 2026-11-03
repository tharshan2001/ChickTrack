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
  "totalSales": "420000.00",
  "notes": "Daily counter sales"
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
    "totalSales": "420000.00",
    "costAmount": "342240.00",
    "profit": "77760.00",
    "notes": "Daily counter sales",
    "createdAt": "2026-01-15T18:00:00Z",
    "updatedAt": "2026-01-15T18:00:00Z"
  },
  "timestamp": "2026-01-15T18:00:00Z"
}
```

`costAmount` is the landed cost of the stock the branch received on `saleDate`, and `profit` is `totalSales - costAmount`.

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

- `totalSales` must be greater than zero.
- `saleDate` is required.
- Branch and chicken type must exist.

## Errors

- `VALIDATION_FAILED`
- `RESOURCE_NOT_FOUND`
