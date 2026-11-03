ALTER TABLE branch_settlement_allocations ADD COLUMN branch_sale_id uuid REFERENCES branch_sales (id);
ALTER TABLE branch_settlement_allocations ALTER COLUMN distribution_id DROP NOT NULL;

CREATE INDEX branch_settlement_allocations_branch_sale_idx ON branch_settlement_allocations (branch_sale_id);
