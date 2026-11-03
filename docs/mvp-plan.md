# Backend MVP Plan

## Goal

Deliver a deployable backend that supports one pilot branch end-to-end:

**OAuth2 login -> master data -> daily price -> procurement -> receiving -> stock batch -> FIFO distribution -> branch receiving -> payment/settlement -> report.**

The mobile app is deferred.

## Scope

### Must have

- OAuth2 Authorization Code + PKCE.
- DB-backed users, roles, branches, and branch scoping.
- Master data: branches, suppliers, chicken types, warehouses, expense categories.
- Effective-dated daily prices.
- Procurement with partial/full receiving.
- Commission methods and procurement expenses.
- Landed cost on received weight.
- Stock batches, movement ledger, adjustments.
- FIFO allocation with audited override.
- Distribution, dispatch, branch receiving, discrepancy reasons.
- Reversal documents for posted records.
- Supplier payments and basic branch settlements.
- Outstanding supplier and branch balances.
- Dashboard and core reports with CSV/PDF exports.
- Audit log, document numbering, idempotency keys.
- Testcontainers integration tests and Docker deployment.

### Deferred

- Mobile app.
- Push notifications.
- S3 photo attachments.
- Excel exports.
- Advanced analytics and consolidated reporting.
- Full accounting ledger, POS, customer invoices, receivables.
- Client-side offline queue.

## Milestones

| Milestone | Duration | Exit Criteria |
|---|---:|---|
| M0 Foundation and OAuth persistence | 1 week | Login with PKCE works; tokens persist; `/api/**` protected; migrations pass. |
| M1 Identity and master data | 1 week | CRUD and branch isolation tested. |
| M2 Daily prices | 0.5 week | Price history never changes saved transactions. |
| M3 Procurement and receiving | 1.5 weeks | Landed-cost examples pass. |
| M4 Inventory and FIFO | 1.5 weeks | Concurrent dispatch cannot oversell. |
| M5 Distribution and branch receiving | 1.5 weeks | End-to-end flow passes. |
| M6 Payments and settlements | 1 week | Balances match fixtures. |
| M7 Dashboard and reports | 1.5 weeks | Known dataset produces expected outputs. |
| M8 Hardening and pilot | 1 week | Restore drill and UAT sign-off. |

## First Vertical Slice

1. OAuth2 login.
2. Create branch and supplier.
3. Set daily price.
4. Create procurement.
5. Receive stock.
6. Create batch.
7. Dispatch to branch.
8. Confirm branch receipt.
9. Show one profitability report.

## Definition of Done

- Unit tests for cost, commission, rounding, FIFO, and allocation.
- Testcontainers PostgreSQL integration tests.
- Concurrency test for batch allocation.
- Security tests for roles and branch isolation.
- OpenAPI contract published.
- Docker Compose deployment verified.
- Backup and restore drill completed.
- No open P0/P1 defects.
