-- Day close: a branch enters its total sales (Rs) and the stock left per chicken type.
-- Sold weight per type = stock before the day - closing stock. The branch owes us on receipt,
-- so settlements pay deliveries (distributions) again, not branch sales.

ALTER TABLE branch_sale_items ADD COLUMN opening_weight_kg numeric(14,3) NOT NULL DEFAULT 0;
ALTER TABLE branch_sale_items ADD COLUMN closing_weight_kg numeric(14,3) NOT NULL DEFAULT 0;
ALTER TABLE branch_sale_items ALTER COLUMN selling_price_per_kg DROP NOT NULL;
ALTER TABLE branch_sale_items ALTER COLUMN revenue DROP NOT NULL;
ALTER TABLE branch_sale_items ALTER COLUMN profit DROP NOT NULL;

ALTER TABLE branch_sale_items DROP CONSTRAINT branch_sale_items_non_negative_check;
ALTER TABLE branch_sale_items ADD CONSTRAINT branch_sale_items_non_negative_check
    CHECK (weight_kg >= 0 AND opening_weight_kg >= 0 AND closing_weight_kg >= 0
        AND closing_weight_kg <= opening_weight_kg
        AND (selling_price_per_kg IS NULL OR selling_price_per_kg >= 0)
        AND transfer_price_per_kg >= 0);

-- Existing rows: what was sold was the whole movement for that day.
UPDATE branch_sale_items SET opening_weight_kg = weight_kg, closing_weight_kg = 0;

ALTER TABLE branch_sales ADD COLUMN expected_sales numeric(16,2);
UPDATE branch_sales s SET expected_sales = s.total_sales;

-- Test data only: settlements that paid branch sales are dropped; new ones pay deliveries.
DELETE FROM branch_settlements
WHERE id IN (SELECT branch_settlement_id FROM branch_settlement_allocations WHERE branch_sale_id IS NOT NULL);
DELETE FROM branch_settlement_allocations WHERE branch_sale_id IS NOT NULL;
