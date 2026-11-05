-- Ledger de partidas dobradas (RFC-001 §5). O balanceamento (débitos = créditos
-- por journal entry e por moeda) é aplicado pela camada de serviço, não por
-- uma constraint declarativa de banco, porque depende de agregação sobre
-- múltiplas linhas de postings.

CREATE TABLE journal_entries (
    id          UUID PRIMARY KEY,
    transfer_id UUID NOT NULL UNIQUE,
    currency    VARCHAR(3) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE postings (
    id               UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries (id),
    account_id       UUID NOT NULL REFERENCES accounts (id),
    direction        VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount           BIGINT NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_postings_journal_entry_id ON postings (journal_entry_id);
CREATE INDEX idx_postings_account_id ON postings (account_id);
