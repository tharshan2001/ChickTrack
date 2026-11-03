CREATE TABLE branches (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL,
    name varchar(150) NOT NULL,
    address text,
    phone varchar(32),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX branches_code_idx ON branches (code);

CREATE TABLE roles (
    id uuid PRIMARY KEY,
    name varchar(50) NOT NULL,
    description varchar(255)
);

CREATE UNIQUE INDEX roles_name_idx ON roles (name);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    username varchar(100) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name varchar(150) NOT NULL,
    email varchar(150),
    phone varchar(32),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX users_username_idx ON users (username);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_branches (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    branch_id uuid NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, branch_id)
);

INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'OWNER', 'Full system access'),
    ('00000000-0000-0000-0000-000000000002', 'PROCUREMENT_MANAGER', 'Manage procurement, suppliers, and purchase prices'),
    ('00000000-0000-0000-0000-000000000003', 'WAREHOUSE_MANAGER', 'Manage stock, batches, and warehouse adjustments'),
    ('00000000-0000-0000-0000-000000000004', 'DISTRIBUTION_MANAGER', 'Manage distribution and dispatch'),
    ('00000000-0000-0000-0000-000000000005', 'DRIVER', 'Confirm deliveries'),
    ('00000000-0000-0000-0000-000000000006', 'BRANCH_RECEIVER', 'Receive branch deliveries and record discrepancies'),
    ('00000000-0000-0000-0000-000000000007', 'ACCOUNTANT', 'Manage payments, settlements, and reports'),
    ('00000000-0000-0000-0000-000000000008', 'VIEWER', 'Read-only access');
