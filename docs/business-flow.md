# ChickTrack Business Logic and Flow

This document is the single reference for how ChickTrack works end to end: who does what, what each step calculates, what is stored, and how money and stock move through the business.

## 1. Actors and Roles

| Role | Purpose | Main APIs |
|---|---|---|
| `OWNER` | Full access, users and roles, settings and reports | everything, including `/users`, `/roles` |
| `PROCUREMENT_MANAGER` | Suppliers, purchase prices, procurement | `/procurements`, `/suppliers`, `/daily-prices` |
| `WAREHOUSE_MANAGER` | Stock, batches, adjustments | `/stock-batches`, `/inventory-movements`, `/stock-adjustments` |
| `DISTRIBUTION_MANAGER` | Distribution, dispatch, reversal | `/distributions` |
| `BRANCH_RECEIVER` | Branch receiving and the daily close | `/distributions/{id}/receipts`, `/branch-sales` |
| `ACCOUNTANT` | Settlements, balances, reports | `/branch-settlements`, `/reports/*`, `/dashboard` |
| `VIEWER` | Read-only reporting | `/reports/*`, `/dashboard` |
| `DRIVER` | Delivery confirmation | (delivery module pending) |

Users are assigned branches (`user_branches`). Every branch-owned read and write (distributions, receiving, sales, settlements, balances, reports, dashboard) is scoped to those branches; a branch outside the user's scope returns `FORBIDDEN`.

- `OWNER` sees every branch.
- Head-office roles (`ACCOUNTANT`, `PROCUREMENT_MANAGER`, `WAREHOUSE_MANAGER`, `DISTRIBUTION_MANAGER`) see every branch unless they are assigned specific branches.
- Every other role sees only its assigned branches. A user with no assigned branches sees none.
- Warehouse-level report rows (stock batches, warehouse movements) are visible only to users with access to every branch.

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
Branch receiving (weight confirmed, discrepancy recorded as loss)  --> branch owes received kg x T
   |
Day close (takings + stock left per type; sold = opening - closing)  --> branch profit, short/over
   |
Branch settlement (branch pays its received deliveries, oldest first)
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
- What the branch owes = `T x received weight` of each delivery, from the moment it is received.

## 3. Step-by-Step Business Logic

### 3.1 Login

- OAuth2 Authorization Code with PKCE. The mobile client generates a code verifier and challenge.
- The user authenticates with the web login form; the authorization server issues an access token and a refresh token.
- The access token carries `roles` as a claim and is mapped to `ROLE_*` authorities on every API call.
- Every `/api/**` endpoint requires a valid bearer token. Bootstrap admin is created on first start (`app.oauth.admin.username` / `app.oauth.admin.password`).
- Document writes are protected by role checks in the controllers (`@PreAuthorize`) and by branch scoping in the services (`BranchAccessService`).

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
- The branch selling price in this list gives the expected sales of a day close (sold weight x price); it does not change what the branch owes.

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
- The batch's receipt time follows the procurement date: now for today, the start of that business day for back-dated procurements, so FIFO issues older stock first. Procurement dates cannot be in the future.
- Reference is allocated from the document sequence: `PRC-2026-000001`.

### 3.5 Warehouse Stock

- `stock_batches` hold received weight, remaining weight, landed cost, and receipt time.
- `inventory_movements` is the ledger of weight changes: `RECEIPT`, `DISTRIBUTION`, `LOSS`, `DAMAGE`, `RETURN`, `ADJUSTMENT`, `REVERSAL`.
- Procurement writes a `RECEIPT` movement for every batch it creates, so warehouse stock reconciles as `RECEIPT - DISTRIBUTION = remaining weight`.
- `LOSS` and `DAMAGE` movements that reference a distribution are branch-side discrepancies: the weight already left the warehouse at dispatch, so they do not reduce batch remaining weight.
- Warehouse stock adjustments (`DAMAGE`, `LOSS`, `RETURN`, `ADJUSTMENT`, reference `STOCK_ADJUSTMENT`) do change remaining weight and are recorded in `stock_adjustments`. Warehouse stock reconciles as `RECEIPT - DISTRIBUTION + REVERSAL + adjustments = remaining weight`.
- Remaining weight is updated inside the same transaction as the distribution and can never go negative.
- FIFO is used for warehouse issues: batches are ordered by receipt time and locked with `SELECT ... FOR UPDATE` before allocation.
- `reserved_weight_kg` is weight held by draft distributions. Available stock = remaining − reserved; new drafts and decreasing adjustments use only available stock.

### 3.6 Distribution to a Branch

The warehouse sends stock to a branch.

- The request carries the branch, the date, and per item: chicken type, weight to send, and the branch-set selling price per kg.
- Stock is allocated FIFO across the available (unreserved) weight of batches of that chicken type and reserved for the draft. If the available weight is short, the whole request is rejected with `INSUFFICIENT_STOCK` and nothing is mutated.
- Each allocated line snapshots: batch, weight, unit cost (landed cost of the batch), unit price (branch-set price).
- Line math: `lineCost = weight × unitCost`, `lineRevenue = weight × unitPrice`, `lineMargin = lineRevenue - lineCost`.
- Distribution expenses are allocated to lines by weight; the last line absorbs rounding.
- Distribution totals: `totalWeightKg`, `totalCost`, `totalRevenue`, `totalMargin`, `expenseAmount`.
- Dispatch turns the reservation into an issue (remaining and reserved weight both drop), moves the distribution to `DISPATCHED` and writes a `DISTRIBUTION` movement per line. Reversing a draft releases its reservation.

### 3.7 Branch Receiving

- The branch confirms the weight it received per line and may give a discrepancy reason (`MELT_WATER`, `DAMAGE`, `WEIGHT_DIFFERENCE`, `SHORT_DELIVERY`, `OTHER`).
- Every dispatched line must be confirmed exactly once; a fully lost line is recorded as `0` received with a reason.
- Received weight cannot exceed the dispatched weight.
- Any discrepancy is written to the ledger as a `LOSS` movement and shows up as shrinkage in the reports.
- After receiving, the distribution is `RECEIVED` and the branch owes `received weight x transfer price` for each line.
- A dispatched distribution can be reversed: the full weight returns to the batches with a `REVERSAL` movement.
- A received distribution can be reversed only while no payment is allocated to it and the branch still holds the received weight (no closed day used it). Only the received weight returns to the batches; recorded discrepancies stay as losses.

### 3.8 Branch Day Close and Profit Marking

At the end of each day the branch counts the stock it has left of each chicken type and enters its total takings (Rs).

- Opening stock per type = weight received by the end of the day (Asia/Colombo) - weight of earlier closed days.
- Sold weight = opening - closing. It includes any waste; closing above opening is refused (`CLOSING_ABOVE_STOCK`). Every type with stock needs a closing count.
- Cost of stock sold (`transferValue`) = the sold weight at the transfer price, taken from the oldest receipts still held. `transferPricePerKg` is that cost divided by the weight.
- Branch profit = `takings - cost of stock sold`.
- Expected sales = `sold weight x branch selling price` (the daily price for that branch, type and date); short/over (`variance`) = `takings - expected`. Without a price the close still saves and expected is unknown.
- Saving the same branch and date again replaces that day's lines and keeps the same reference (`SALE-2026-000001`). Only the latest closed day can change, because its closing stock is the next day's opening stock.
- Day closes, settlements, receipts and reversals for one branch run one at a time (the branch row is locked).

### 3.9 Branch Settlement

- A settlement records what the branch paid: branch, date, amount, method, reference, notes.
- Allocations point at the received deliveries being paid and must sum to the settlement amount. The app allocates oldest delivery first.
- An allocation cannot exceed what is still owed on that delivery, and the delivery must belong to the settlement's branch and be received.
- A posted settlement can only be reversed; reversal restores the outstanding balance.
- Branch outstanding = received value of all received deliveries − posted settlements.

### 3.10 Dashboard and Reports

- Dashboard cards: procurement value and weight, stock weight and value, distribution weight and transfer value, our margin, total sales, branch profit, branch outstanding (received in the period minus paid in the period), shrinkage.
- Reports: procurement, stock by batch, distribution (transfer value and our margin), sales (takings, expected, short/over, cost of stock sold, branch profit), profit by branch, shrinkage, settlements.
- Reports read posted data only: draft and reversed procurements and distributions are excluded, reversed settlements are excluded.
- Exports are generated server-side in CSV or PDF from the same rows and totals the API returns.

## 4. Money Reconciliation

| Figure | Source of truth |
|---|---|
| Purchase cost | `procurements.total_cost` (purchase + commission + expenses) |
| Stock value | `stock_batches.remaining_weight_kg × landed_cost_per_kg` |
| Dispatch value | `distributions.total_cost` and `distributions.total_revenue` |
| Branch sales | `branch_sales.total_sales` |
| Branch cost of stock sold | `branch_sales.transfer_value` (opening − closing weight, costed against branch stock still held, oldest receipt first) |
| Branch profit | `branch_sales.branch_profit` = total sales − cost of stock sold |
| Short / over | `branch_sales.total_sales − branch_sales.expected_sales` |
| Outstanding | Σ received weight × transfer price of received deliveries − posted settlement allocations |
| Shrinkage | `inventory_movements` of type `LOSS` / `DAMAGE` |

## 5. Validations and Error Codes

| Code | When |
|---|---|
| `VALIDATION_FAILED` | Body or parameter validation, unsupported export format |
| `RESOURCE_NOT_FOUND` | Unknown id in the request |
| `BUSINESS_RULE_VIOLATION` | Rule breach, e.g. allocations not summing to the amount |
| `INSUFFICIENT_STOCK` | Not enough warehouse stock for a distribution |
| `OVER_ALLOCATION` | Settlement allocation above what is still owed on the delivery |
| `CLOSING_ABOVE_STOCK` | A day close counts more stock than the branch held |
| `INVALID_STATUS_TRANSITION` | Action not allowed in the current status |
| `FORBIDDEN` | Branch outside the authenticated user's scope, or role not allowed |
| `CONCURRENT_MODIFICATION` | The record changed in another request; reload and retry |
| `DATA_CONFLICT` | The request conflicts with existing data (unique or foreign key) |
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
| Inventory | `stock_batches`, `inventory_movements`, `stock_adjustments` |
| Distribution | `distributions`, `distribution_items`, `distribution_expenses`, `expense_allocations`, `branch_receipts`, `branch_receipt_items` |
| Sales | `branch_sales`, `branch_sale_items` |
| Finance | `branch_settlements`, `branch_settlement_allocations` |

## 7. Not Yet Implemented

- Push notifications, photo attachments, Excel exports.
