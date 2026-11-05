CREATE TABLE accounts (
    id               UUID PRIMARY KEY,
    owner_id         UUID NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    overdraft_policy VARCHAR(16) NOT NULL CHECK (overdraft_policy IN ('FORBIDDEN', 'ALLOWED')),
    status           VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);
