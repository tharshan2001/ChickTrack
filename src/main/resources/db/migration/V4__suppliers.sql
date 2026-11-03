CREATE TABLE suppliers (
    id uuid PRIMARY KEY,
    name varchar(150) NOT NULL,
    contact_person varchar(150),
    phone varchar(32),
    email varchar(150),
    address text,
    payment_terms varchar(255),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX suppliers_name_idx ON suppliers (name);
CREATE INDEX suppliers_active_idx ON suppliers (active);
