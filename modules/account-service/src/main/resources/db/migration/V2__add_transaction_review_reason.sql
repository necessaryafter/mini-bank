-- A fraud decision from the transaction-processor can reject or hold a transfer;
-- we keep the human-readable reason(s) for audit. Nullable: an approved (or not
-- yet reviewed) transfer has none.
ALTER TABLE transactions ADD COLUMN review_reason TEXT;
