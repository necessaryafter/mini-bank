package com.carbonbank.account.service

import com.carbonbank.account.controller.dto.CreateAccountRequest
import com.carbonbank.account.entity.Account
import com.carbonbank.account.entity.Entry
import com.carbonbank.account.entity.Transaction
import com.carbonbank.account.support.AccountTestConfig
import com.carbonbank.common.testcontainers.PostgresTestcontainer
import com.carbonbank.common.testcontainers.RedisTestcontainer
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionStatus
import com.carbonbank.common.transaction.TransactionType
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import com.carbonbank.common.web.ApiException
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.util.UUID

// Context configuration is intentionally identical to every other
// account-service @SpringBootTest — see AccountTestConfig for why.
@SpringBootTest(properties = ["outbox.poll-interval-ms=3600000", "outbox.initial-delay-ms=3600000"])
@Import(PostgresTestcontainer::class, RedisTestcontainer::class, AccountTestConfig::class)
@ActiveProfiles("test")
class StatementServiceTest {

    @Autowired
    lateinit var statementService: StatementService

    @Autowired
    lateinit var accountService: AccountService

    @Test
    fun `lists movements newest first with the account header`() {
        val account = openAccount("Ada", "100.00") // opening deposit posts one credit
        addMovement(account, "10.00", EntryStatus.POSTED)
        addMovement(account, "20.00", EntryStatus.POSTED)

        val statement = statementService.getStatement(account, cursor = null, limit = 50)

        assertEquals("Ada", statement.ownerName)
        assertEquals(3, statement.lines.size)
        val sequences = statement.lines.map { it.sequence }
        assertEquals(sequences.sortedDescending(), sequences, "lines must be newest first")
        assertNull(statement.nextCursor)
    }

    @Test
    fun `keyset pagination walks the whole history without gaps or repeats`() {
        val account = openAccount("Grace", "0.00") // zero opening balance posts no entry
        repeat(5) { addMovement(account, "${it + 1}.00", EntryStatus.POSTED) }

        val collected = mutableListOf<Long>()
        var cursor: Long? = null
        do {
            val page = statementService.getStatement(account, cursor, limit = 2)
            assertTrue(page.lines.size <= 2)
            collected += page.lines.map { it.sequence }
            cursor = page.nextCursor
        } while (cursor != null)

        assertEquals(5, collected.size)
        assertEquals(collected.distinct().size, collected.size, "no sequence should repeat across pages")
        assertEquals(collected.sortedDescending(), collected, "pages must stay globally newest-first")
    }

    @Test
    fun `excludes pending and voided holds but keeps reverted movements`() {
        val account = openAccount("Alan", "0.00")
        addMovement(account, "1.00", EntryStatus.POSTED)
        addMovement(account, "2.00", EntryStatus.PENDING)
        addMovement(account, "3.00", EntryStatus.VOIDED)
        addMovement(account, "4.00", EntryStatus.REVERTED)

        val statement = statementService.getStatement(account, cursor = null, limit = 50)

        assertEquals(2, statement.lines.size)
        assertEquals(setOf(EntryStatus.POSTED, EntryStatus.REVERTED), statement.lines.map { it.status }.toSet())
    }

    @Test
    fun `reading the statement of an unknown account is a 404`() {
        val ex = assertThrows(ApiException::class.java) {
            statementService.getStatement(UUID.randomUUID(), cursor = null, limit = 50)
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    private fun openAccount(name: String, initialBalance: String): UUID =
        accountService.createAccount(
            CreateAccountRequest(ownerName = name, initialBalance = BigDecimal(initialBalance)),
            IdempotencyKey.random(),
        ).id

    // Posts a standalone credit entry so the tests can build a history with a
    // controlled mix of statuses, independent of the transfer pipeline.
    private fun addMovement(accountId: UUID, amount: String, entryStatus: EntryStatus) = transaction {
        val account = Account.findById(accountId)!!
        val movement = Transaction.new {
            description = "movement ${entryStatus.name.lowercase()}"
            idempotencyKey = IdempotencyKey.random()
            status = TransactionStatus.COMPLETED
        }
        Entry.new {
            transaction = movement
            this.account = account
            entryType = TransactionType.CREDIT
            this.amount = Money.from(BigDecimal(amount))
            status = entryStatus
            balanceAfter = if (entryStatus == EntryStatus.POSTED) Money.from(BigDecimal(amount)) else null
        }
    }
}
