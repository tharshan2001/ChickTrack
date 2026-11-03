CREATE TABLE procurements (
    id uuid PRIMARY KEY,
    reference varchar(40) NOT NULL,
    supplier_id uuid NOT NULL REFERENCES suppliers (id),
    procurement_date date NOT NULL,
    status varchar(30) NOT NULL,
    notes text,
    purchase_value numeric(16,2) NOT NULL DEFAULT 0,
    commission_amount numeric(16,2) NOT NULL DEFAULT 0,
    expense_amount numeric(16,2) NOT NULL DEFAULT 0,
    total_cost numeric(16,2) NOT NULL DEFAULT 0,
    paid_weight_kg numeric(14,3) NOT NULL DEFAULT 0,
    received_weight_kg numeric(14,3) NOT NULL DEFAULT 0,
    landed_cost_per_kg numeric(14,4),
    reversal_reason text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX procurements_reference_idx ON procurements (reference);
CREATE INDEX procurements_supplier_idx ON procurements (supplier_id);
CREATE INDEX procurements_status_idx ON procurements (status);
CREATE INDEX procurements_date_idx ON procurements (procurement_date);

CREATE TABLE procurement_items (
    id uuid PRIMARY KEY,
    procurement_id uuid NOT NULL REFERENCES procurements (id) ON DELETE CASCADE,
    chicken_type_id uuid NOT NULL REFERENCES chicken_types (id),
    paid_weight_kg numeric(14,3) NOT NULL,
    purchase_price_per_kg numeric(14,4) NOT NULL,
    commission_method varchar(20) NOT NULL,
    commission_value numeric(14,4) NOT NULL DEFAULT 0,
    quantity integer,
    purchase_value numeric(16,2) NOT NULL DEFAULT 0,
    commission_amount numeric(16,2) NOT NULL DEFAULT 0,
    allocated_expense_amount numeric(16,2) NOT NULL DEFAULT 0,
    total_cost numeric(16,2) NOT NULL DEFAULT 0,
    received_weight_kg numeric(14,3) NOT NULL DEFAULT 0,
    landed_cost_per_kg numeric(14,4),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX procurement_items_procurement_idx ON procurement_items (procurement_id);
CREATE INDEX procurement_items_chicken_type_idx ON procurement_items (chicken_type_id);

CREATE TABLE procurement_expenses (
    id uuid PRIMARY KEY,
    procurement_id uuid NOT NULL REFERENCES procurements (id) ON DELETE CASCADE,
    category_id uuid REFERENCES expense_categories (id),
    description text,
    amount numeric(16,2) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX procurement_expenses_procurement_idx ON procurement_expenses (procurement_id);

CREATE TABLE procurement_receipts (
    id uuid PRIMARY KEY,
    procurement_id uuid NOT NULL REFERENCES procurements (id) ON DELETE CASCADE,
    received_at timestamptz NOT NULL,
    notes text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX procurement_receipts_procurement_idx ON procurement_receipts (procurement_id);

CREATE TABLE procurement_receipt_items (
    id uuid PRIMARY KEY,
    receipt_id uuid NOT NULL REFERENCES procurement_receipts (id) ON DELETE CASCADE,
    procurement_item_id uuid NOT NULL REFERENCES procurement_items (id),
    received_weight_kg numeric(14,3) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX procurement_receipt_items_receipt_idx ON procurement_receipt_items (receipt_id);
CREATE INDEX procurement_receipt_items_item_idx ON procurement_receipt_items (procurement_item_id);
