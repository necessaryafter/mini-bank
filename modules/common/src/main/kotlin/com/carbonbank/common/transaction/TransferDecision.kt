package com.carbonbank.common.transaction

/**
 * The transaction-processor's verdict as account-service needs to act on it —
 * the coreography contract between the two services (docs/adr/0003). Kept
 * deliberately coarse: the processor's internal score, bands and per-rule
 * signals stay on its side; account-service only needs to know what to do.
 */
enum class TransferDecision {
    /** Risk is acceptable; proceed to balance capture. */
    APPROVED,

    /** Rejected as fraud; fail the transfer without capturing. */
    REJECTED,

    /** Too risky to auto-capture, not clearly fraud; hold for review. */
    UNDER_REVIEW,
}
