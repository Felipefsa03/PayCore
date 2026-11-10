-- V5 owns the unique constraint scoped by owner, operation and key.
DROP INDEX idx_transfers_idempotency_key;
CREATE INDEX idx_transfers_source_created ON transfers (source_account_id, created_at);
