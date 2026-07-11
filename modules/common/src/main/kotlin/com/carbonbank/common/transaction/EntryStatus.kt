package com.carbonbank.common.transaction

/**
 * Status of a single ledger entry (a hold on one account).
 *
 * - `PENDING` splits into two different terminal states depending on whether
 * the entry ever took effect:
 * - `VOIDED`: rejected while still a hold (e.g. insufficient funds) — never
 *   affected the balance, nothing to undo.
 * - `POSTED`: captured, affected the balance. Can later move to `REVERTED`
 *   if a compensating action undoes it (e.g. credit failed after this debit
 *   was already posted) — that's the only case with something to reverse.
 *
 * Shares the POSTED->REVERTED spelling with [TransactionStatus] on purpose:
 * same concept (a completed effect being undone), no domain reason to spell
 * it differently between the two.
 */
enum class EntryStatus {
    PENDING,
    POSTED,
    VOIDED,
    REVERTED,
}
