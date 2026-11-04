# Feature Documentation

Each document describes the business purpose, rules, data, API surface, and current implementation status of a feature.

The end-to-end business logic and flow is documented in [Business Flow](../business-flow.md).

| Feature | Status | Document |
|---|---|---|
| Authentication and OAuth2 | Implemented foundation | [authentication.md](authentication.md) |
| Identity and master data | In progress | [master-data.md](master-data.md) |
| Daily prices | Planned | [pricing.md](pricing.md) |
| Company margin rules | Implemented | [margin-rules.md](margin-rules.md) |
| Procurement and receiving | Planned | [procurement.md](procurement.md) |
| Inventory and batches | Planned | [inventory.md](inventory.md) |
| Distribution and branch receiving | Planned | [distribution.md](distribution.md) |
| Branch sales and profit marking | Implemented | [branch-sales.md](branch-sales.md) |
| Payments and settlements | Planned | [finance.md](finance.md) |
| Dashboard and reports | Implemented | [reports.md](reports.md) |
| Audit and idempotency | Foundation ready | [audit-and-idempotency.md](audit-and-idempotency.md) |

## Status Definitions

- **Implemented**: code and tests exist.
- **Implemented foundation**: core infrastructure exists, feature APIs may still be missing.
- **In progress**: schema or partial implementation exists.
- **Planned**: not implemented yet.
