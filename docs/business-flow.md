# ChickTrack Business Logic and Flow

This document is the single reference for how ChickTrack works end to end: who does what, what each step calculates, what is stored, and how money and stock move through the business.

## 1. Actors and Roles

| Role | Purpose | Main APIs |
|---|---|---|
| `OWNER` | Full access, owns settings and reports | everything |
| `PROCUREMENT_MANAGER` | Suppliers, purchase prices, procurement | `/procurements`, `/suppliers`, `/daily-prices` |
| `WAREHOUSE_MANAGER` | Stock, batches, adjustments | (inventory APIs pending) |
| `DISTRIBUTION_MANAGER` | Distribution, dispatch, reversal | `/distributions` |
| `BRANCH_RECEIVER` | Branch receiving and daily sales entry | `/distributions/{id}/receipts`, `/branch-sales` |
| `ACCOUNTANT` | Settlements, balances, reports | `/branch-settlements`, `/reports/*`, `/dashboard` |
| `VIEWER` | Read-only reporting | `/reports/*`, `/dashboard` |
| `DRIVER` | Delivery confirmation | (delivery module pending) |

Users are assigned branches (`user_branches`). Report and dashboard queries are scoped to those branches; requesting a branch outside the user's scope returns `FORBIDDEN`.

## 2. End-to-End Flow

```
Login (OAuth2 PKCE)
   |
Master data (branches, suppliers, chicken types, expense categories)
   |
Prices (branch selling price per chicken type + our margin rule)
   |
Procurement (purchase price + commission + expenses)  --> stock batch created automatically
   |
Warehouse stock (batches, remaining weight, FIFO)
   |
Distribution to branch (transfer price = landed cost + our margin + expenses)
   |
Branch receiving (weight confirmed, discrepancy recorded as loss)
   |
Branch sales (weight sold per chicken type, branch price)  --> branch profit marked
   |
Branch settlement (branch pays the transfer value)
   |
Dashboard and reports (CSV / PDF export)
```

### The price ladder (per kg)

```
purchase price (typed at purchase)          P
+ commission                                 C
+ allocated procurement expenses             E
= landed cost (warehouse)                    L
+ our margin (L x markup %)                  M
+ allocated distribution expenses            D
= transfer price (branch pays us)            T
+ branch margin                              S - T
= branch selling price (we set per branch)   S
```

- Our profit per kg = `M` (distribution expenses are passed through in `T`).
- Branch profit per kg = `S - T`.
- What the branch owes = `T x weight` (the transfer value).

## 3. Step-by-Step Business Logic

### 3.1 Login

- OAuth2 Authorization Code with PKCE. The mobile client generates a code verifier and challenge.
- The user authenticates with the web login form; the authorization server issues an access token and a refresh token.
- The access token carries `roles` as a claim and is mapped to `ROLE_*` authorities on every API call.
- Every `/api/**` endpoint requires a valid bearer token. Bootstrap admin is created on first start (`app.oauth.admin.username` / `app.oauth.admin.password`).
- All document writes are protected by role checks in the controller plus branch scoping in the service layer.

### 3.2 Master Data

- Branches: code (unique), name, address, phone, active flag.
- Suppliers: name, contact, phone, email, address, payment terms, active flag.
- Chicken types: name (unique), description, active flag.
- Expense categories: name, description, active flag.
- Master data is soft-disabled rather than deleted, so historic documents stay readable.

### 3.3 Daily Prices

- A daily price row stores the purchase price per kg, the selling price per kg, and an `effectiveFrom` instant.
- A price can be global, per branch (the branch-set selling price), or per supplier.
- Prices are effective dated: saved transactions snapshot the price they used, so later price changes never rewrite history.
- The branch selling price in this list is the price applied when the branch records its sales.

### 3.3b Company Margin Rules

- A rule sets our markup percentage for a chicken type, optionally scoped to a branch, effective from a point in time.
- The rule applied to a dispatch is the newest one effective on or before the distribution date, preferring a branch-specific rule.
- Our margin per kg = `landed cost x markup percent`. Without a rule the margin is zero.

### 3.4 Procurement (no approval, no receiving step, no reversal)

The branch buys live weight from a supplier. One procurement is created and posted immediately.

Per item:

| Calculation | Formula |
|---|---|
| Purchase value | `paidWeightKg × purchasePricePerKg` |
| Commission | `FIXED`: value; `PER_KG`: `paidWeightKg × value`; `PERCENTAGE`: `purchaseValue × value / 100`; `PER_BIRD`: `value × quantity` |
| Allocated expense | procurement expenses split across items by paid weight (last item absorbs rounding) |
| Item total cost | `purchaseValue + commission + allocated expense` |
| Landed cost per kg | `item total cost ÷ received weight` |

Document totals: `purchaseValue`, `commissionAmount`, `expenseAmount`, `totalCost`, `paidWeightKg`, `receivedWeightKg`, `landedCostPerKg`.

Rules:

- Paid weight is treated as received weight; there is no partial receiving.
- Status is `RECEIVED` from creation. No approval, receiving, or reversal step exists.
- A stock batch is created automatically for every item with weight: `receivedWeightKg = paidWeightKg`, `remainingWeightKg = paidWeightKg`, `landedCostPerKg` from above.
- Reference is allocated from the document sequence: `PRC-2026-000001`.

### 3.5 Warehouse Stock

- `stock_batches` hold received weight, remaining weight, landed cost, and receipt time.
- `inventory_movements` is the ledger of weight changes: `RECEIPT`, `DISTRIBUTION`, `LOSS`, `DAMAGE`, `RETURN`, `ADJUSTMENT`, `REVERSAL`.
- Procurement writes a `RECEIPT` movement for every batch it creates, so warehouse stock reconciles as `RECEIPT - DISTRIBUTION = remaining weight`.
- `LOSS` and `DAMAGE` movements are branch-side discrepancies: the weight already left the warehouse at dispatch, so they do not reduce batch remaining weight.
- Remaining weight is updated inside the same transaction as the distribution and can never go negative.
- FIFO is used **only** for warehouse issues: batches are ordered by receipt time and locked with `SELECT ... FOR UPDATE` before allocation.

### 3.6 Distribution to a Branch

The warehouse sends stock to a branch.

- The request carries the branch, the date, and per item: chicken type, weight to send, and the branch-set selling price per kg.
- Stock is allocated FIFO across batches of that chicken type. If the available weight is short, the whole request is rejected with `INSUFFICIENT_STOCK` and nothing is mutated.
- Each allocated line snapshots: batch, weight, unit cost (landed cost of the batch), unit price (branch-set price).
- Line math: `lineCost = weight × unitCost`, `lineRevenue = weight × unitPrice`, `lineMargin = lineRevenue - lineCost`.
- Distribution expenses are allocated to lines by weight; the last line absorbs rounding.
- Distribution totals: `totalWeightKg`, `totalCost`, `totalRevenue`, `totalMargin`, `expenseAmount`.
- Dispatch moves the distribution to `DISPATCHED` and writes a `DISTRIBUTION` movement per line.

### 3.7 Branch Receiving

- The branch confirms the weight it received per line and may give a discrepancy reason (`MELT_WATER`, `DAMAGE`, `WEIGHT_DIFFERENCE`, `SHORT_DELIVERY`, `OTHER`).
- Received weight cannot exceed the dispatched weight.
- Any discrepancy is written to the ledger as a `LOSS` movement and shows up as shrinkage in the reports.
- After receiving, the distribution is `RECEIVED`. A dispatched or received distribution can be reversed, which returns the weight to the batches and writes a `REVERSAL` movement.

### 3.8 Branch Daily Sales and Profit Marking

The branch does not report quantities or chicken types and no FIFO costing happens at the branch.

- The branch enters **one total sales amount per branch per day**.
- Cost for that day = landed cost of the stock the branch **received** that day: `received weight × landed cost assigned at dispatch`.
- Profit or loss = `totalSales - cost`.
- Recording the same branch and date again replaces the earlier figure and keeps the same reference (`SALE-2026-000001`).
- The cost is captured when the day's sales are recorded; a receipt booked later does not retroactively change it.

### 3.9 Branch Settlement

- A settlement records what the branch paid: branch, date, amount, method, reference, notes.
- Allocations point at the branch sales being paid and must sum to the settlement amount.
- An allocation cannot exceed the outstanding amount of that sale, and the sale must belong to the settlement's branch.
- A posted settlement can only be reversed; reversal restores the outstanding balance.
- Branch outstanding = total entered sales − posted settlements.

### 3.10 Dashboard and Reports

- Dashboard cards: procurement value and weight, stock weight and value, distribution weight and transfer value, our margin, total sales, branch profit, branch outstanding, shrinkage.
- Reports: procurement, stock by batch, distribution (transfer value and our margin), sales (revenue, transfer value, branch profit), profit by branch, shrinkage, settlements.
- Reports read posted data only: draft and reversed procurements and distributions are excluded, reversed settlements are excluded.
- Exports are generated server-side in CSV or PDF from the same rows and totals the API returns.

## 4. Money Reconciliation

| Figure | Source of truth |
|---|---|
| Purchase cost | `procurements.total_cost` (purchase + commission + expenses) |
| Stock value | `stock_batches.remaining_weight_kg × landed_cost_per_kg` |
| Dispatch value | `distributions.total_cost` and `distributions.total_revenue` |
| Branch sales | `branch_sales.total_sales` |
| Sales cost | `branch_sales.cost_amount` (stock received that day at dispatch cost) |
| Profit | `branch_sales.profit` = total sales − cost |
| Outstanding | entered sales − posted settlement allocations |
| Shrinkage | `inventory_movements` of type `LOSS` / `DAMAGE` |

## 5. Validations and Error Codes

| Code | When |
|---|---|
| `VALIDATION_FAILED` | Body or parameter validation, unsupported export format |
| `RESOURCE_NOT_FOUND` | Unknown id in the request |
| `BUSINESS_RULE_VIOLATION` | Rule breach, e.g. allocations not summing to the amount |
| `INSUFFICIENT_STOCK` | Not enough warehouse stock for a distribution |
| `OVER_ALLOCATION` | Settlement allocation above the sale transfer value |
| `INSUFFICIENT_BRANCH_STOCK` | Branch sold more than it received |
| `PRICE_NOT_CONFIGURED` | No branch selling price for the chicken type and date |
| `INVALID_STATUS_TRANSITION` | Action not allowed in the current status |
| `FORBIDDEN` | Branch outside the authenticated user's scope |
| `REPORT_NOT_FOUND` | Unknown report name in an export request |
| `REPORT_GENERATION_FAILED` | Export rendering failed |

## 6. Data Tables

| Area | Tables |
|---|---|
| Identity | `users`, `roles`, `user_roles`, `user_branches`, `branches` |
| OAuth2 | `oauth2_*` (Spring Authorization Server schema) |
| Foundation | `audit_logs`, `document_sequences`, `sync_requests` |
| Reference | `suppliers`, `chicken_types`, `expense_categories` |
| Pricing | `daily_prices` |
| Margins | `company_margin_rules` |
| Procurement | `procurements`, `procurement_items`, `procurement_expenses`, `procurement_receipts`, `procurement_receipt_items` |
| Inventory | `stock_batches`, `inventory_movements` |
| Distribution | `distributions`, `distribution_items`, `distribution_expenses`, `expense_allocations`, `branch_receipts`, `branch_receipt_items` |
| Sales | `branch_sales`, `branch_sale_items` |
| Finance | `branch_settlements`, `branch_settlement_allocations` |

## 7. Not Yet Implemented

- Audit log writes (`audit_logs` table exists, nothing writes to it yet).
- Idempotency key handling (`sync_requests` table exists, no filter or interceptor).
- Inventory read and adjustment APIs (`stock-batches`, `inventory-movements`, `stock-adjustments`).
- User and role administration API (branch users cannot be created yet).
- Push notifications, photo attachments, Excel exports.
