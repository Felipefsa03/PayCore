-- Esqueleto da tabela de transferências (RF-03). idempotency_key é um
-- placeholder simplificado para este card de bootstrap: o card dedicado
-- "Idempotência com Idempotency-Key e fingerprint" introduz o fingerprint do
-- payload e o escopo por cliente/operação descritos em
-- docs/architecture/modelo-dominio-estados-transferencia.md §1.4.

CREATE TABLE transfers (
    id                     UUID PRIMARY KEY,
    source_account_id      UUID NOT NULL REFERENCES accounts (id),
    destination_account_id UUID NOT NULL REFERENCES accounts (id),
    amount                 BIGINT NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3) NOT NULL,
    idempotency_key        VARCHAR(200) NOT NULL,
    correlation_id         UUID NOT NULL,
    state                  VARCHAR(16) NOT NULL
        CHECK (state IN ('RECEIVED', 'VALIDATING', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED')),
    failure_reason         TEXT,
    journal_entry_id       UUID REFERENCES journal_entries (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transfers_distinct_accounts CHECK (source_account_id <> destination_account_id)
);

CREATE UNIQUE INDEX idx_transfers_idempotency_key ON transfers (idempotency_key);
CREATE INDEX idx_transfers_source_account_id ON transfers (source_account_id);
CREATE INDEX idx_transfers_destination_account_id ON transfers (destination_account_id);
