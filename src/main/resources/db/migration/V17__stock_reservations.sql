-- Weight held for draft distributions. Available stock = remaining - reserved.
ALTER TABLE stock_batches ADD COLUMN reserved_weight_kg numeric(14,3) NOT NULL DEFAULT 0;

-- Existing drafts start holding their stock. If drafts already over-reserve a batch, cap at the remaining weight.
UPDATE stock_batches b
SET reserved_weight_kg = LEAST(b.remaining_weight_kg, drafts.weight)
FROM (
    SELECT di.stock_batch_id, SUM(di.weight_kg) AS weight
    FROM distribution_items di
    JOIN distributions d ON d.id = di.distribution_id
    WHERE d.status = 'DRAFT'
    GROUP BY di.stock_batch_id
) drafts
WHERE drafts.stock_batch_id = b.id;

ALTER TABLE stock_batches ADD CONSTRAINT stock_batches_reserved_check
    CHECK (reserved_weight_kg >= 0 AND reserved_weight_kg <= remaining_weight_kg);

DROP INDEX IF EXISTS stock_batches_available_idx;
CREATE INDEX stock_batches_available_idx ON stock_batches (chicken_type_id, received_at)
    WHERE remaining_weight_kg > reserved_weight_kg;
