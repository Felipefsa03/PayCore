CREATE TABLE idempotency_records (
    id                 UUID PRIMARY KEY,
    owner_id           UUID NOT NULL,
    operation          VARCHAR(64) NOT NULL,
    idempotency_key    VARCHAR(200) NOT NULL,
    fingerprint        VARCHAR(64) NOT NULL,
    status             VARCHAR(16) NOT NULL
        CHECK (status IN ('PROCESSING', 'COMPLETED')),
    transfer_id        UUID REFERENCES transfers (id),
    response_snapshot  JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_owner_operation_key
        UNIQUE (owner_id, operation, idempotency_key)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_records (expires_at);
