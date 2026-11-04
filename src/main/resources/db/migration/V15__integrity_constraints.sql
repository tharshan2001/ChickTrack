-- Status values
ALTER TABLE procurements ADD CONSTRAINT procurements_status_check
    CHECK (status IN ('DRAFT', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'REVERSED'));
ALTER TABLE distributions ADD CONSTRAINT distributions_status_check
    CHECK (status IN ('DRAFT', 'DISPATCHED', 'RECEIVED', 'CLOSED', 'REVERSED'));
ALTER TABLE branch_settlements ADD CONSTRAINT branch_settlements_status_check
    CHECK (status IN ('POSTED', 'REVERSED'));
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_type_check
    CHECK (type IN ('RECEIPT', 'DISTRIBUTION', 'RETURN', 'DAMAGE', 'LOSS', 'ADJUSTMENT', 'REVERSAL'));
ALTER TABLE procurement_items ADD CONSTRAINT procurement_items_commission_method_check
    CHECK (commission_method IN ('FIXED', 'PER_KG', 'PERCENTAGE', 'PER_BIRD'));
ALTER TABLE distribution_expenses ADD CONSTRAINT distribution_expenses_allocation_method_check
    CHECK (allocation_method IN ('BY_WEIGHT', 'FIXED', 'MANUAL'));
ALTER TABLE branch_receipt_items ADD CONSTRAINT branch_receipt_items_reason_check
    CHECK (discrepancy_reason IS NULL
        OR discrepancy_reason IN ('MELT_WATER', 'DAMAGE', 'WEIGHT_DIFFERENCE', 'SHORT_DELIVERY', 'OTHER'));

-- Non-negative weights, prices and amounts
ALTER TABLE procurement_items ADD CONSTRAINT procurement_items_non_negative_check
    CHECK (paid_weight_kg > 0 AND received_weight_kg >= 0 AND purchase_price_per_kg >= 0
        AND commission_value >= 0 AND (quantity IS NULL OR quantity >= 0));
ALTER TABLE procurement_expenses ADD CONSTRAINT procurement_expenses_amount_check CHECK (amount >= 0);
ALTER TABLE stock_batches ADD CONSTRAINT stock_batches_weights_check
    CHECK (received_weight_kg >= 0 AND remaining_weight_kg <= received_weight_kg AND landed_cost_per_kg >= 0);
ALTER TABLE inventory_movements ADD CONSTRAINT inventory_movements_weight_check CHECK (weight_kg >= 0);
ALTER TABLE distribution_items ADD CONSTRAINT distribution_items_non_negative_check
    CHECK (weight_kg > 0 AND unit_cost >= 0 AND transfer_price_per_kg >= 0);
ALTER TABLE distribution_expenses ADD CONSTRAINT distribution_expenses_amount_check CHECK (amount >= 0);
ALTER TABLE branch_receipt_items ADD CONSTRAINT branch_receipt_items_weights_check
    CHECK (received_weight_kg >= 0 AND discrepancy_weight_kg >= 0);
ALTER TABLE daily_prices ADD CONSTRAINT daily_prices_non_negative_check
    CHECK (purchase_price_per_kg >= 0 AND selling_price_per_kg >= 0);
ALTER TABLE company_margin_rules ADD CONSTRAINT company_margin_rules_markup_check CHECK (markup_percent >= 0);
ALTER TABLE branch_sale_items ADD CONSTRAINT branch_sale_items_non_negative_check
    CHECK (weight_kg > 0 AND selling_price_per_kg >= 0 AND transfer_price_per_kg >= 0);
ALTER TABLE branch_settlements ADD CONSTRAINT branch_settlements_amount_check CHECK (amount > 0);
ALTER TABLE branch_settlement_allocations ADD CONSTRAINT branch_settlement_allocations_amount_check CHECK (amount > 0);

-- A settlement allocation pays exactly one document
ALTER TABLE branch_settlement_allocations ADD CONSTRAINT branch_settlement_allocations_target_check
    CHECK ((branch_sale_id IS NOT NULL) <> (distribution_id IS NOT NULL));

-- One margin rule per chicken type, branch scope and effective instant
CREATE UNIQUE INDEX company_margin_rules_unique_idx
    ON company_margin_rules (chicken_type_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), effective_from);

-- Reporting lookups
CREATE INDEX branch_sales_date_idx ON branch_sales (sale_date);
CREATE INDEX stock_batches_available_idx ON stock_batches (chicken_type_id, received_at) WHERE remaining_weight_kg > 0;
