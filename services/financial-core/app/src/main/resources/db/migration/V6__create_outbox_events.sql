-- Transactional Outbox (RF-06).
--
-- A linha de outbox é gravada na MESMA transação que confirma o pagamento:
-- ou o pagamento e o evento existem juntos, ou nenhum dos dois existe.
--
-- O publicador consome linhas PENDING com FOR UPDATE SKIP LOCKED, o que permite
-- múltiplas instâncias concorrentes sem publicar o mesmo evento em duplicidade
-- dentro da mesma transação. Como o efeito externo (broker) acontece antes do
-- commit, a semântica efetiva é at-least-once: uma queda entre a publicação e o
-- commit pode reenviar o evento, e o consumidor deve ser idempotente.

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(120) NOT NULL,
    event_version   INTEGER NOT NULL CHECK (event_version > 0),
    payload         JSONB NOT NULL,
    correlation_id  UUID NOT NULL,
    status          VARCHAR(16) NOT NULL
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts        INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_outbox_published_at
        CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

-- Índice parcial: o publicador só varre o que está pendente e vencido.
CREATE INDEX idx_outbox_due ON outbox_events (available_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_correlation ON outbox_events (correlation_id);
