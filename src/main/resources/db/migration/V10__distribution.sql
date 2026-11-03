CREATE TABLE distributions (
    id uuid PRIMARY KEY,
    reference varchar(40) NOT NULL,
    branch_id uuid NOT NULL REFERENCES branches (id),
    distribution_date date NOT NULL,
    status varchar(20) NOT NULL,
    total_weight_kg numeric(14,3) NOT NULL DEFAULT 0,
    total_cost numeric(16,2) NOT NULL DEFAULT 0,
    total_revenue numeric(16,2) NOT NULL DEFAULT 0,
    total_margin numeric(16,2) NOT NULL DEFAULT 0,
    expense_amount numeric(16,2) NOT NULL DEFAULT 0,
    notes text,
    reversal_reason text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX distributions_reference_idx ON distributions (reference);
CREATE INDEX distributions_branch_idx ON distributions (branch_id);
CREATE INDEX distributions_status_idx ON distributions (status);
CREATE INDEX distributions_date_idx ON distributions (distribution_date);

CREATE TABLE distribution_items (
    id uuid PRIMARY KEY,
    distribution_id uuid NOT NULL REFERENCES distributions (id) ON DELETE CASCADE,
    stock_batch_id uuid NOT NULL REFERENCES stock_batches (id),
    chicken_type_id uuid NOT NULL REFERENCES chicken_types (id),
    weight_kg numeric(14,3) NOT NULL,
    unit_cost numeric(14,4) NOT NULL,
    unit_price numeric(14,4) NOT NULL,
    line_cost numeric(16,2) NOT NULL,
    line_revenue numeric(16,2) NOT NULL,
    line_margin numeric(16,2) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX distribution_items_distribution_idx ON distribution_items (distribution_id);
CREATE INDEX distribution_items_batch_idx ON distribution_items (stock_batch_id);
CREATE INDEX distribution_items_chicken_type_idx ON distribution_items (chicken_type_id);

CREATE TABLE distribution_expenses (
    id uuid PRIMARY KEY,
    distribution_id uuid NOT NULL REFERENCES distributions (id) ON DELETE CASCADE,
    category_id uuid REFERENCES expense_categories (id),
    description text,
    amount numeric(16,2) NOT NULL,
    allocation_method varchar(20) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX distribution_expenses_distribution_idx ON distribution_expenses (distribution_id);

CREATE TABLE expense_allocations (
    id uuid PRIMARY KEY,
    distribution_expense_id uuid NOT NULL REFERENCES distribution_expenses (id) ON DELETE CASCADE,
    distribution_item_id uuid NOT NULL REFERENCES distribution_items (id) ON DELETE CASCADE,
    amount numeric(16,2) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX expense_allocations_expense_idx ON expense_allocations (distribution_expense_id);
CREATE INDEX expense_allocations_item_idx ON expense_allocations (distribution_item_id);

CREATE TABLE branch_receipts (
    id uuid PRIMARY KEY,
    distribution_id uuid NOT NULL REFERENCES distributions (id) ON DELETE CASCADE,
    received_at timestamptz NOT NULL,
    notes text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX branch_receipts_distribution_idx ON branch_receipts (distribution_id);

CREATE TABLE branch_receipt_items (
    id uuid PRIMARY KEY,
    branch_receipt_id uuid NOT NULL REFERENCES branch_receipts (id) ON DELETE CASCADE,
    distribution_item_id uuid NOT NULL REFERENCES distribution_items (id),
    received_weight_kg numeric(14,3) NOT NULL,
    discrepancy_weight_kg numeric(14,3) NOT NULL DEFAULT 0,
    discrepancy_reason varchar(30),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX branch_receipt_items_receipt_idx ON branch_receipt_items (branch_receipt_id);
CREATE INDEX branch_receipt_items_item_idx ON branch_receipt_items (distribution_item_id);
