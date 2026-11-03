CREATE TABLE inventory_movements (
    id uuid PRIMARY KEY,
    stock_batch_id uuid NOT NULL REFERENCES stock_batches (id),
    type varchar(20) NOT NULL,
    weight_kg numeric(14,3) NOT NULL,
    reference_type varchar(50),
    reference_id uuid,
    reason text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX inventory_movements_batch_idx ON inventory_movements (stock_batch_id);
CREATE INDEX inventory_movements_reference_idx ON inventory_movements (reference_type, reference_id);
CREATE INDEX inventory_movements_type_idx ON inventory_movements (type);
