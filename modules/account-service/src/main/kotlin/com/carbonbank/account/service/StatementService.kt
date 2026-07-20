package com.carbonbank.account.service

import com.carbonbank.account.controller.dto.StatementLineResponse
import com.carbonbank.account.controller.dto.StatementResponse
import com.carbonbank.account.entity.Account
import com.carbonbank.account.table.Entries
import com.carbonbank.account.table.Transactions
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.web.ApiException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read-only projection of the ledger into an account statement (RF04). There is
 * no separate statement store: the `entries` table is the source of truth, so a
 * statement is just an ordered, filtered query over it — see docs/adr/0012.
 */
@Service
class StatementService {

    /**
     * One page of movements, newest first. Paginates by keyset on `sequence`
     * (the `idx_entries_account_id_sequence` index), not by offset: a ledger is
     * append-only, so seeking `sequence < cursor` stays cheap and never skips or
     * repeats a row when new entries land between page fetches.
     */
    @Transactional(readOnly = true)
    fun getStatement(accountId: UUID, cursor: Long?, limit: Int): StatementResponse {
        val account = loadAccount(accountId)
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)

        // Fetch one extra row to tell whether an older page exists without a count query.
        val rows = movementsOf(accountId, SortOrder.DESC)
            .apply { if (cursor != null) andWhere { Entries.sequence less cursor } }
            .limit(pageSize + 1)
            .toList()

        val hasOlder = rows.size > pageSize
        val page = if (hasOlder) rows.take(pageSize) else rows

        return account.toStatement(
            lines = page.map { it.toLine() },
            nextCursor = if (hasOlder) page.last()[Entries.sequence] else null,
        )
    }

    /** The whole history, oldest first — the shape a printable statement/PDF wants. */
    @Transactional(readOnly = true)
    fun fullStatement(accountId: UUID): StatementResponse {
        val account = loadAccount(accountId)
        val lines = movementsOf(accountId, SortOrder.ASC).map { it.toLine() }
        return account.toStatement(lines = lines, nextCursor = null)
    }

    private fun movementsOf(accountId: UUID, order: SortOrder) =
        (Entries innerJoin Transactions)
            .selectAll()
            .andWhere { Entries.account eq accountId }
            .andWhere { Entries.status inList STATEMENT_STATUSES }
            .orderBy(Entries.sequence, order)

    private fun ResultRow.toLine(): StatementLineResponse =
        StatementLineResponse(
            entryId = this[Entries.id].value,
            transactionId = this[Entries.transaction].value,
            type = this[Entries.entryType],
            amount = this[Entries.amount].toBigDecimal(),
            balanceAfter = this[Entries.balanceAfter]?.toBigDecimal(),
            status = this[Entries.status],
            description = this[Transactions.description],
            createdAt = this[Entries.createdAt],
            sequence = this[Entries.sequence],
        )

    private fun Account.toStatement(lines: List<StatementLineResponse>, nextCursor: Long?): StatementResponse =
        StatementResponse(
            accountId = id.value,
            ownerName = ownerName,
            currentBalance = currentBalance().toBigDecimal(),
            lines = lines,
            nextCursor = nextCursor,
        )

    private fun loadAccount(accountId: UUID): Account =
        Account.findById(accountId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Account $accountId not found")

    companion object {
        const val MAX_PAGE_SIZE = 200

        // Only settled movements belong on a statement. PENDING/VOIDED holds never
        // moved money; REVERTED is included because a reversal is a real, auditable
        // event the account owner must see (TASK RF04 asks for reverted transfers).
        private val STATEMENT_STATUSES = listOf(EntryStatus.POSTED, EntryStatus.REVERTED)
    }
}
