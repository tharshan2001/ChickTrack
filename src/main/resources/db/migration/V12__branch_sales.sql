CREATE TABLE branch_sales (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id),
    reference varchar(40) NOT NULL,
    sale_date date NOT NULL,
    total_sales numeric(16,2) NOT NULL,
    cost_amount numeric(16,2) NOT NULL,
    profit numeric(16,2) NOT NULL,
    notes text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX branch_sales_reference_idx ON branch_sales (reference);
CREATE UNIQUE INDEX branch_sales_branch_date_idx ON branch_sales (branch_id, sale_date);
