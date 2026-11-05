-- Running costs not tied to a procurement or a distribution: rent, salaries, electricity, vehicles.
-- A null branch_id is a head-office expense.
CREATE TABLE operating_expenses (
    id uuid PRIMARY KEY,
    reference varchar(40) NOT NULL,
    expense_date date NOT NULL,
    expense_category_id uuid NOT NULL REFERENCES expense_categories (id),
    branch_id uuid REFERENCES branches (id),
    amount numeric(14,2) NOT NULL,
    payee varchar(150),
    description text,
    status varchar(20) NOT NULL DEFAULT 'POSTED',
    void_reason text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operating_expenses_amount_check CHECK (amount > 0),
    CONSTRAINT operating_expenses_status_check CHECK (status IN ('POSTED', 'VOIDED')),
    CONSTRAINT operating_expenses_void_reason_check CHECK (status <> 'VOIDED' OR length(trim(void_reason)) > 0)
);

CREATE UNIQUE INDEX operating_expenses_reference_idx ON operating_expenses (reference);
CREATE INDEX operating_expenses_date_idx ON operating_expenses (expense_date);
CREATE INDEX operating_expenses_branch_idx ON operating_expenses (branch_id);
CREATE INDEX operating_expenses_category_idx ON operating_expenses (expense_category_id);
