package com.carbonbank.account.statement

import com.carbonbank.account.controller.dto.StatementLineResponse
import com.carbonbank.account.controller.dto.StatementResponse
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class StatementPdfRendererTest {

    private val renderer = StatementPdfRenderer()

    @Test
    fun `renders a non-empty pdf with the magic header`() {
        val statement = StatementResponse(
            accountId = UUID.randomUUID(),
            ownerName = "Ada Lovelace",
            currentBalance = BigDecimal("60.00"),
            lines = listOf(
                line(2, TransactionType.DEBIT, "40.00", "60.00"),
                line(1, TransactionType.CREDIT, "100.00", "100.00"),
            ),
            nextCursor = null,
        )

        val bytes = renderer.render(statement)

        assertTrue(bytes.size > 100, "a rendered statement should not be trivially small")
        assertEquals("%PDF", bytes.decodeToString(0, 4))
    }

    @Test
    fun `renders an empty statement without failing`() {
        val statement = StatementResponse(
            accountId = UUID.randomUUID(),
            ownerName = "Nobody",
            currentBalance = BigDecimal("0.00"),
            lines = emptyList(),
            nextCursor = null,
        )

        val bytes = renderer.render(statement)

        assertEquals("%PDF", bytes.decodeToString(0, 4))
    }

    private fun line(sequence: Long, type: TransactionType, amount: String, balanceAfter: String?) =
        StatementLineResponse(
            entryId = UUID.randomUUID(),
            transactionId = UUID.randomUUID(),
            type = type,
            amount = BigDecimal(amount),
            balanceAfter = balanceAfter?.let(::BigDecimal),
            status = EntryStatus.POSTED,
            description = "Test movement",
            createdAt = Instant.parse("2026-07-19T10:31:00Z"),
            sequence = sequence,
        )
}
