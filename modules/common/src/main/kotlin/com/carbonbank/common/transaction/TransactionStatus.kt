package com.carbonbank.common.transaction

/**
 * Status of a transfer as exposed by the API (matches the contract in
 * `GET /transfers/{id}`). Distinct from [EntryStatus]: a transaction can go
 * straight to FAILED without ever having posted entries, whereas an entry's
 * lifecycle only starts once the transaction has passed that check.
 */
enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERTED,
}
