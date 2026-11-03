CREATE TABLE chicken_types (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    description varchar(255),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX chicken_types_name_idx ON chicken_types (name);

CREATE TABLE warehouses (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    location varchar(255),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX warehouses_name_idx ON warehouses (name);

CREATE TABLE expense_categories (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    description varchar(255),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX expense_categories_name_idx ON expense_categories (name);
