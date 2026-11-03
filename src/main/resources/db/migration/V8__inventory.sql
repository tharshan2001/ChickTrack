CREATE TABLE stock_batches (
    id uuid PRIMARY KEY,
    procurement_item_id uuid NOT NULL REFERENCES procurement_items (id),
    chicken_type_id uuid NOT NULL REFERENCES chicken_types (id),
    received_weight_kg numeric(14,3) NOT NULL,
    remaining_weight_kg numeric(14,3) NOT NULL,
    landed_cost_per_kg numeric(14,4) NOT NULL,
    received_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_batches_remaining_weight_check CHECK (remaining_weight_kg >= 0)
);

CREATE INDEX stock_batches_chicken_type_idx ON stock_batches (chicken_type_id);
CREATE INDEX stock_batches_procurement_item_idx ON stock_batches (procurement_item_id);
