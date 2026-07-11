package com.carbonbank.account.service

import com.carbonbank.account.entity.Transaction
import com.carbonbank.account.table.Accounts
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionStatus
import com.carbonbank.common.transaction.TransactionType
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Applies the transaction-processor's decision to a PENDING transaction —
 * the only place that writes [EntryStatus.POSTED]/[EntryStatus.VOIDED], since
 * account-service is the sole owner of the ledger (transaction-processor has
 * no DB access of its own; see docs/adr/0003).
 */
@Service
class TransferCaptureService {

    /**
     * Locks both accounts involved (in a fixed id order, so two transfers
     * touching the same pair of accounts in opposite directions cannot
     * deadlock each other), re-validates the available balance under that
     * lock, and then captures or voids the entries accordingly.
     *
     * The [Transaction.status] guard only works if it runs *after* the lock
     * is acquired: two concurrent deliveries of the same SQS message can both
     * observe PENDING before either transaction commits, so checking the
     * status first and locking afterwards allows the loser of the race to post
     * the transaction a second time once it is unblocked. Locking first and
     * then re-reading the status via [Transaction.refresh] ensures the second
     * transaction observes the first transaction's committed result and exits
     * without doing any work, making redelivery idempotent.
     *
     * READ COMMITTED is required (Exposed defaults to REPEATABLE READ). Under
     * REPEATABLE READ, the transaction snapshot is fixed at the first query,
     * so after waiting for the account lock, [com.carbonbank.account.entity.Account.currentBalance] would still
     * observe the pre-lock snapshot instead of the balance committed by the
     * previous lock holder. That can silently overwrite committed updates
     * (lost updates) instead of detecting the conflict.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun capture(transactionId: UUID) {
        val transaction = Transaction.findById(transactionId) ?: return
        val entries = transaction.entries.toList()
        val debit = entries.single { it.entryType == TransactionType.DEBIT }
        val credit = entries.single { it.entryType == TransactionType.CREDIT }

        listOf(debit.account.id.value, credit.account.id.value)
            .sorted()
            .forEach(::lockAccount)

        transaction.refresh(flush = false)
        if (transaction.status != TransactionStatus.PENDING) return

        val debitBalance = debit.account.currentBalance()
        if (debitBalance < debit.amount) {
            debit.status = EntryStatus.VOIDED
            debit.touch()

            credit.status = EntryStatus.VOIDED
            credit.touch()

            transaction.status = TransactionStatus.FAILED
            transaction.touch()
            return
        }

        // creditBalance must be read before either entry's status flips to
        // POSTED: flipping first flushes the credit entry itself (now POSTED,
        // with a higher sequence than the account's real last entry, but no
        // balanceAfter yet), which currentBalance() would then pick up as
        // "latest" and collapse to Money.ZERO instead of the real balance.
        val creditBalance = credit.account.currentBalance()

        debit.status = EntryStatus.POSTED
        debit.balanceAfter = debitBalance - debit.amount
        debit.touch()

        credit.status = EntryStatus.POSTED
        credit.balanceAfter = creditBalance + credit.amount
        credit.touch()

        transaction.status = TransactionStatus.COMPLETED
        transaction.touch()
    }

    /**
     * Fails a transfer the processor rejected as fraud. Voids the pending entries
     * and marks the transaction FAILED with the reason. Locks and re-checks the
     * status under the lock for the same idempotency reason as [capture]: a
     * redelivered decision must not act twice.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun reject(transactionId: UUID, reason: String) {
        val transaction = Transaction.findById(transactionId) ?: return
        val entries = transaction.entries.toList()
        val debit = entries.single { it.entryType == TransactionType.DEBIT }
        val credit = entries.single { it.entryType == TransactionType.CREDIT }

        listOf(debit.account.id.value, credit.account.id.value)
            .sorted()
            .forEach(::lockAccount)

        transaction.refresh(flush = false)
        if (transaction.status != TransactionStatus.PENDING) return

        debit.status = EntryStatus.VOIDED
        debit.touch()

        credit.status = EntryStatus.VOIDED
        credit.touch()

        transaction.status = TransactionStatus.FAILED
        transaction.reviewReason = reason
        transaction.touch()
    }

    /**
     * Holds a transfer the processor flagged as too risky to auto-capture but not
     * clearly fraud. It stays PENDING with the reason recorded — the entries are
     * left PENDING, so no balance moves, and a review process (out of scope, see
     * docs/adr/0010) would later capture or reject it. No account lock is needed
     * since no entries are written.
     */
    @Transactional
    fun hold(transactionId: UUID, reason: String) {
        val transaction = Transaction.findById(transactionId) ?: return
        if (transaction.status != TransactionStatus.PENDING) return
        transaction.reviewReason = reason
        transaction.touch()
    }

    /**
     * `SELECT ... FOR UPDATE` on the account row is a lock on a proxy, not on
     * the entries it guards — it only works because every write path for
     * that account's entries goes through this same gate first.
     */
    private fun lockAccount(id: UUID) {
        Accounts.selectAll()
            .andWhere { Accounts.id eq id }
            .forUpdate(ForUpdateOption.ForUpdate)
            .toList()
    }
}
