CREATE TABLE stock_adjustments (
    id uuid PRIMARY KEY,
    reference varchar(40) NOT NULL,
    stock_batch_id uuid NOT NULL REFERENCES stock_batches (id),
    type varchar(20) NOT NULL,
    weight_change_kg numeric(14,3) NOT NULL,
    reason text NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stock_adjustments_type_check CHECK (type IN ('DAMAGE', 'LOSS', 'RETURN', 'ADJUSTMENT')),
    CONSTRAINT stock_adjustments_direction_check CHECK (
        weight_change_kg <> 0
        AND (type NOT IN ('DAMAGE', 'LOSS') OR weight_change_kg < 0)
        AND (type <> 'RETURN' OR weight_change_kg > 0)),
    CONSTRAINT stock_adjustments_reason_check CHECK (length(trim(reason)) > 0)
);

CREATE UNIQUE INDEX stock_adjustments_reference_idx ON stock_adjustments (reference);
CREATE INDEX stock_adjustments_batch_idx ON stock_adjustments (stock_batch_id);
CREATE INDEX stock_adjustments_created_at_idx ON stock_adjustments (created_at);
