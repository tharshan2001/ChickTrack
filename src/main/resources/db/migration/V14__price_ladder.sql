CREATE TABLE company_margin_rules (
    id uuid PRIMARY KEY,
    chicken_type_id uuid NOT NULL REFERENCES chicken_types (id),
    branch_id uuid REFERENCES branches (id),
    markup_percent numeric(7,4) NOT NULL,
    effective_from timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX company_margin_rules_type_idx ON company_margin_rules (chicken_type_id, effective_from);
CREATE INDEX company_margin_rules_branch_idx ON company_margin_rules (branch_id);

ALTER TABLE distribution_items ADD COLUMN our_margin_per_kg numeric(14,4) NOT NULL DEFAULT 0;
ALTER TABLE distribution_items ADD COLUMN distribution_expense_per_kg numeric(14,4) NOT NULL DEFAULT 0;
ALTER TABLE distribution_items ADD COLUMN transfer_price_per_kg numeric(14,4) NOT NULL DEFAULT 0;
ALTER TABLE distribution_items ADD COLUMN line_transfer_value numeric(16,2) NOT NULL DEFAULT 0;
ALTER TABLE distribution_items DROP COLUMN unit_price;
ALTER TABLE distribution_items DROP COLUMN line_revenue;
ALTER TABLE distribution_items DROP COLUMN line_margin;

ALTER TABLE distributions ADD COLUMN total_transfer_value numeric(16,2) NOT NULL DEFAULT 0;
ALTER TABLE distributions ADD COLUMN total_our_margin numeric(16,2) NOT NULL DEFAULT 0;
ALTER TABLE distributions DROP COLUMN total_revenue;
ALTER TABLE distributions DROP COLUMN total_margin;

CREATE TABLE branch_sale_items (
    id uuid PRIMARY KEY,
    branch_sale_id uuid NOT NULL REFERENCES branch_sales (id) ON DELETE CASCADE,
    chicken_type_id uuid NOT NULL REFERENCES chicken_types (id),
    weight_kg numeric(14,3) NOT NULL,
    selling_price_per_kg numeric(14,4) NOT NULL,
    transfer_price_per_kg numeric(14,4) NOT NULL,
    revenue numeric(16,2) NOT NULL,
    cost numeric(16,2) NOT NULL,
    profit numeric(16,2) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX branch_sale_items_sale_idx ON branch_sale_items (branch_sale_id);
CREATE INDEX branch_sale_items_chicken_type_idx ON branch_sale_items (chicken_type_id);

ALTER TABLE branch_sales ADD COLUMN transfer_value numeric(16,2) NOT NULL DEFAULT 0;
ALTER TABLE branch_sales ADD COLUMN branch_profit numeric(16,2) NOT NULL DEFAULT 0;
ALTER TABLE branch_sales DROP COLUMN cost_amount;
ALTER TABLE branch_sales DROP COLUMN profit;
