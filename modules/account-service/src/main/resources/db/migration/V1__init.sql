CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL UNIQUE,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERTED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions (id),
    account_id UUID NOT NULL REFERENCES accounts (id),
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'POSTED', 'VOIDED', 'REVERTED')),
    balance_after BIGINT,
    -- DB-assigned monotonic counter: id is a random UUIDv4 with no ordering
    -- guarantee, so this is what "latest entry for this account" orders by.
    sequence BIGSERIAL UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_entries_transaction_id ON entries (transaction_id);

-- Finds the latest entry for an account (the balance_after cursor) without
-- scanning the whole history: ORDER BY sequence DESC LIMIT 1 WHERE account_id = ?.
CREATE INDEX idx_entries_account_id_sequence ON entries (account_id, sequence DESC);

-- Outbox: events are written here in the same transaction as the business
-- change that produced them (see docs/adr/0006), then a poller publishes them
-- to SQS and stamps published_at. published_at IS NULL means "not yet shipped".
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The poller only ever scans for not-yet-published rows, oldest first; a
-- partial index keeps that scan cheap even as published history accumulates.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
