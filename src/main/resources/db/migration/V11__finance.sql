CREATE TABLE branch_settlements (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id),
    settlement_date date NOT NULL,
    amount numeric(16,2) NOT NULL,
    method varchar(40) NOT NULL,
    reference varchar(100),
    notes text,
    status varchar(20) NOT NULL,
    reversal_reason text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX branch_settlements_branch_idx ON branch_settlements (branch_id);
CREATE INDEX branch_settlements_date_idx ON branch_settlements (settlement_date);
CREATE INDEX branch_settlements_status_idx ON branch_settlements (status);

CREATE TABLE branch_settlement_allocations (
    id uuid PRIMARY KEY,
    branch_settlement_id uuid NOT NULL REFERENCES branch_settlements (id) ON DELETE CASCADE,
    distribution_id uuid NOT NULL REFERENCES distributions (id),
    amount numeric(16,2) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX branch_settlement_allocations_settlement_idx ON branch_settlement_allocations (branch_settlement_id);
CREATE INDEX branch_settlement_allocations_distribution_idx ON branch_settlement_allocations (distribution_id);
