# Identity and Master Data

## Status

In progress. Schema and identity foundation exist; business APIs are planned.

## Purpose

Maintain the reference data required by procurement, inventory, distribution, and reporting.

## Features

### Branches

- Code, name, address, phone.
- Active flag.
- Users are assigned to one or more branches.

### Users and Roles

- Username, password hash, full name, email, phone.
- Active flag.
- Multiple roles per user.
- Multiple branch assignments per user.

### Suppliers

- Name, contact person, phone, address.
- Payment terms and active flag.
- Purchase history and outstanding balance.

### Chicken Types

- Name and description.
- Active flag.
- Used by prices, procurement, batches, and distribution.


### Expense Categories

- Name and description.
- Used by procurement and distribution expenses.

## Rules

- Codes and usernames are unique.
- Master data is deactivated, not deleted.
- All changes are audited.
- Branch-scoped users only see their branches.
- Suppliers with posted transactions remain visible in history even when inactive.

## Data

- `branches`
- `users`
- `roles`
- `user_roles`
- `user_branches`
- `suppliers` (planned)
- `chicken_types` (planned)
- `expense_categories` (planned)

## API

Planned under `/api/v1`:

- `/branches`
- `/users`
- `/roles`
- `/suppliers`
- `/chicken-types`
- `/expense-categories`

See [Master Data API](../api/master-data.md).

## Acceptance Criteria

- Duplicate codes are rejected.
- Inactive records cannot be used in new transactions.
- Branch isolation is tested.
- Master data changes appear in the audit log.
