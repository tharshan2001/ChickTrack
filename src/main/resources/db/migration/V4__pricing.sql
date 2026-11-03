CREATE TABLE daily_prices (
    id uuid PRIMARY KEY,
    chicken_type_id uuid NOT NULL REFERENCES chicken_types (id),
    branch_id uuid REFERENCES branches (id),
    supplier_id uuid REFERENCES suppliers (id),
    purchase_price_per_kg numeric(14,4) NOT NULL,
    selling_price_per_kg numeric(14,4) NOT NULL,
    effective_from timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX daily_prices_lookup_idx
    ON daily_prices (chicken_type_id, branch_id, supplier_id, effective_from);
CREATE INDEX daily_prices_effective_from_idx
    ON daily_prices (effective_from);
