-- Durable idempotency backstop for POST /accounts (Redis is the fast path that
-- resolves the concurrent race; this unique index is what survives a cache flush,
-- mirroring transactions.idempotency_key). Nullable because the genesis account
-- and test-created accounts carry no client key, and Postgres allows multiple
-- NULLs under a UNIQUE constraint.
ALTER TABLE accounts ADD COLUMN idempotency_key VARCHAR(36) UNIQUE;

-- System account that issues the money backing every opening balance: an opening
-- deposit debits this account and credits the new one, keeping the ledger's
-- sum(debits) = sum(credits) invariant. It is a liability/issuance account and
-- WILL go negative by design — the "never negative" rule applies to user accounts,
-- not to this issuer. See docs/adr/0011.
INSERT INTO accounts (id, owner_name)
VALUES ('00000000-0000-0000-0000-000000000000', 'SYSTEM: opening-balance issuance')
ON CONFLICT (id) DO NOTHING;
