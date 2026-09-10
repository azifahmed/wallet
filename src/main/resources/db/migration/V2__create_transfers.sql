CREATE TYPE transfer_status AS ENUM ('COMPLETED', 'DECLINED');

CREATE TABLE IF NOT EXISTS transfers (
    id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    from_wallet_id    UUID            NOT NULL REFERENCES wallets(id),
    to_wallet_id      UUID            NOT NULL REFERENCES wallets(id),
    amount_paise      BIGINT          NOT NULL,
    idempotency_key   VARCHAR(255)    NOT NULL,
    status            transfer_status NOT NULL DEFAULT 'COMPLETED',
    decline_reason    VARCHAR(100),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT transfers_amount_positive     CHECK (amount_paise > 0),
    CONSTRAINT transfers_different_wallets   CHECK (from_wallet_id != to_wallet_id),
    CONSTRAINT transfers_idempotency_key_uq  UNIQUE (idempotency_key)
);

CREATE INDEX idx_transfers_idempotency ON transfers(idempotency_key);
CREATE INDEX idx_transfers_from_wallet ON transfers(from_wallet_id);
CREATE INDEX idx_transfers_to_wallet   ON transfers(to_wallet_id);
