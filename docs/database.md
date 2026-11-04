# Database

## Migration Strategy

Flyway owns all schema changes. Migrations are immutable once applied.

| Migration | Purpose |
|---|---|
| `V1__oauth2.sql` | Spring Authorization Server JDBC tables |
| `V2__identity.sql` | Branches, users, roles, and join tables |
| `V3__foundation.sql` | Audit logs, document sequences, idempotency records |

| `V4__suppliers.sql` | Suppliers |
| `V5__reference_data.sql` | Chicken types and expense categories |
| `V6__pricing.sql` | Daily prices (chicken type, optional branch, optional supplier) |
| `V7__procurement.sql` | Procurement, items, receipts, expenses |
| `V8__inventory.sql` | Stock batches |
| `V9__inventory_movements.sql` | Inventory movement ledger |
| `V10__distribution.sql` | Distributions, items, expense allocations, branch receipts |
| `V11__finance.sql` | Branch settlements and allocations |
| `V12__branch_sales.sql` | Daily branch sales |
| `V13__settlements_on_branch_sales.sql` | Settlements allocate against branch sales |
| `V14__price_ladder.sql` | Company margin rules, transfer pricing, branch sale lines |
| `V15__integrity_constraints.sql` | Status and non-negative `CHECK` constraints, allocation target check, margin rule uniqueness, reporting indexes |
| `V16__stock_adjustments.sql` | Warehouse stock adjustments |
| `V17__stock_reservations.sql` | Reserved weight on batches for draft distributions |

## Current Tables

### OAuth2

- `oauth2_registered_client`
- `oauth2_authorization`
- `oauth2_authorization_consent`

These follow the Spring Authorization Server JDBC schema, adapted for PostgreSQL.

### Identity

- `branches`
- `users`
- `roles`
- `user_roles`
- `user_branches`

Roles are seeded by `V2__identity.sql`.

### Foundation

- `audit_logs`
- `document_sequences`
- `sync_requests`

## Entity Conventions

- Primary key: `uuid`.
- Optimistic lock: `version bigint`.
- Audit timestamps: `created_at timestamptz`, `updated_at timestamptz`.
- Soft deactivation: `active boolean`.
- Foreign keys are enforced by the database.
- Status columns use `CHECK` constraints once transaction tables are added.

## Money and Weight Rules

- Money and weight columns must be `numeric`.
- Weights use `numeric(14,3)`.
- Unit prices and costs use `numeric(14,4)`.
- Line amounts use `numeric(16,2)`.
- Application code uses `BigDecimal`.
- SQL and Java must apply the same rounding rules.

## Inventory Rules

- `inventory_movements` is the source of truth.
- `stock_batches.remaining_weight` is a transactional projection.
- `remaining_weight` must never be negative.
- FIFO allocation locks candidate batches with `SELECT ... FOR UPDATE`.
- A final issue from a batch consumes its remaining cost so the batch reconciles to zero.

## Document Rules

- Posted documents are never edited.
- Corrections use reversal documents with a reason.
- Document numbers are allocated transactionally from `document_sequences`.
- Mobile-originated mutations use an idempotency key recorded in `sync_requests`.

## Indexing

Add indexes for:

- Branch and date-range reporting.
- Supplier and date-range reporting.
- Batch status and remaining weight.
- Distribution and receipt status.
- Document reference numbers.
- Idempotency keys.
