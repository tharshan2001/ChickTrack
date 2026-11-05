# Operating Expenses API

## Status

Implemented. All endpoints require `OWNER` or `ACCOUNTANT`.

Operating expenses are running costs that do not belong to a procurement or a distribution: rent, salaries, electricity, vehicle running costs. They reduce net profit in the [profit and loss report](reports.md#profit-and-loss). Each expense uses an [expense category](master-data.md) and belongs to a branch or, with no `branchId`, to head office.

Procurement and distribution expenses are not entered here: they are part of those documents and already inside the landed cost and transfer price.

## Endpoints

```http
GET  /api/v1/expenses?from=2026-11-01&to=2026-11-30&categoryId=...&branchId=...&headOffice=true&status=POSTED&page=0&size=20
GET  /api/v1/expenses/{id}
POST /api/v1/expenses
PUT  /api/v1/expenses/{id}
POST /api/v1/expenses/{id}/void
```

Create or update:

```json
{
  "expenseDate": "2026-11-05",
  "categoryId": "33333333-3333-3333-3333-333333333333",
  "branchId": "11111111-1111-1111-1111-111111111111",
  "amount": "45000.00",
  "payee": "Kandy landlord",
  "description": "November rent"
}
```

Leave out `branchId` for a head-office expense.

Response (`201`):

```json
{
  "data": {
    "id": "44444444-4444-4444-4444-444444444444",
    "reference": "EXP-2026-000001",
    "expenseDate": "2026-11-05",
    "categoryId": "33333333-3333-3333-3333-333333333333",
    "categoryName": "Rent",
    "branchId": "11111111-1111-1111-1111-111111111111",
    "branchName": "Kandy",
    "amount": "45000.00",
    "payee": "Kandy landlord",
    "description": "November rent",
    "status": "POSTED",
    "voidReason": null,
    "version": 0,
    "createdAt": "2026-11-05T09:00:00Z",
    "updatedAt": "2026-11-05T09:00:00Z"
  },
  "timestamp": "2026-11-05T09:00:00Z"
}
```

Void: `{ "reason": "Entered twice" }`.

## Rules

- `amount` is greater than zero, two decimals at most.
- `expenseDate` cannot be in the future.
- New expenses need an active category.
- Expenses are never deleted. A wrong expense is voided with a reason; voided expenses cannot be edited and are left out of reports.
- Head-office expenses (no branch) can be created and seen only by users who see every branch. A branch-scoped user sees only their branches' expenses.
- Every create, update and void is audited.

## Errors

- `VALIDATION_FAILED` (bad body, bad `status` filter, `from` after `to`)
- `RESOURCE_NOT_FOUND` (expense, category or branch)
- `BUSINESS_RULE_VIOLATION` (future date, inactive category; `409` when the expense is voided)
- `FORBIDDEN`
