package com.carbonbank.account.service

import com.carbonbank.account.entity.Account
import com.carbonbank.account.entity.Entry
import com.carbonbank.account.entity.Transaction
import com.carbonbank.account.support.AccountTestConfig
import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent
import com.carbonbank.common.testcontainers.PostgresTestcontainer
import com.carbonbank.common.testcontainers.RedisTestcontainer
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionStatus
import com.carbonbank.common.transaction.TransactionType
import com.carbonbank.common.transaction.TransferDecision
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

// Context configuration is intentionally identical to every other
// account-service @SpringBootTest — see AccountTestConfig for why.
@SpringBootTest(properties = ["outbox.poll-interval-ms=3600000", "outbox.initial-delay-ms=3600000"])
@Import(PostgresTestcontainer::class, RedisTestcontainer::class, AccountTestConfig::class)
@ActiveProfiles("test")
class TransferDecisionServiceTest {

    @Autowired
    lateinit var decisionService: TransferDecisionService

    private fun newAccount(): UUID = transaction { Account.new { ownerName = "Test" }.id.value }

    private fun seedBalance(accountId: UUID, amount: Money) = transaction {
        val account = Account.findById(accountId)!!
        val opening = Transaction.new {
            description = "Opening balance"
            idempotencyKey = IdempotencyKey.random()
            status = TransactionStatus.COMPLETED
        }
        Entry.new {
            this.transaction = opening
            this.account = account
            entryType = TransactionType.CREDIT
            this.amount = amount
            status = EntryStatus.POSTED
            balanceAfter = amount
        }
    }

    private fun pendingTransfer(fromId: UUID, toId: UUID, amount: Money): UUID = transaction {
        val tx = Transaction.new {
            description = "Transfer"
            idempotencyKey = IdempotencyKey.random()
            status = TransactionStatus.PENDING
        }
        Entry.new {
            this.transaction = tx
            account = Account.findById(fromId)!!
            entryType = TransactionType.DEBIT
            this.amount = amount
            status = EntryStatus.PENDING
        }
        Entry.new {
            this.transaction = tx
            account = Account.findById(toId)!!
            entryType = TransactionType.CREDIT
            this.amount = amount
            status = EntryStatus.PENDING
        }
        tx.id.value
    }

    private fun loadTransaction(id: UUID) = transaction {
        Transaction.findById(id)!!.let { Triple(it.status, it.reviewReason, it.entries.map { e -> e.status }) }
    }

    private fun decision(transferId: UUID, decision: TransferDecision, reasons: List<String>) =
        TransactionDecisionEvent(
            transferId = transferId,
            decision = decision,
            score = 0,
            reasons = reasons,
            idempotencyKey = IdempotencyKey.random(),
            timestamp = Instant.now(),
        )

    @Test
    fun `an approved decision captures the transfer`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))
        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))

        decisionService.apply(decision(transferId, TransferDecision.APPROVED, emptyList()))

        val (status, reason, entries) = loadTransaction(transferId)
        assertEquals(TransactionStatus.COMPLETED, status)
        assertNull(reason)
        assertEquals(listOf(EntryStatus.POSTED, EntryStatus.POSTED), entries)
    }

    @Test
    fun `a rejected decision voids the entries, fails the transfer, and records the reason`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))
        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))

        decisionService.apply(decision(transferId, TransferDecision.REJECTED, listOf("Destination reported")))

        val (status, reason, entries) = loadTransaction(transferId)
        assertEquals(TransactionStatus.FAILED, status)
        assertEquals("Destination reported", reason)
        assertEquals(listOf(EntryStatus.VOIDED, EntryStatus.VOIDED), entries)
    }

    @Test
    fun `an under-review decision holds the transfer as pending with a reason`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))
        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))

        decisionService.apply(
            decision(transferId, TransferDecision.UNDER_REVIEW, listOf("Unusual hour", "Unknown device")),
        )

        val (status, reason, entries) = loadTransaction(transferId)
        assertEquals(TransactionStatus.PENDING, status)
        assertEquals("Unusual hour; Unknown device", reason)
        assertEquals(listOf(EntryStatus.PENDING, EntryStatus.PENDING), entries)
    }

    @Test
    fun `redelivering a rejected decision is idempotent`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))
        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))

        val event = decision(transferId, TransferDecision.REJECTED, listOf("fraud"))
        decisionService.apply(event)
        decisionService.apply(event)

        val (status, _, entries) = loadTransaction(transferId)
        assertEquals(TransactionStatus.FAILED, status)
        assertEquals(listOf(EntryStatus.VOIDED, EntryStatus.VOIDED), entries)
        assertNotNull(loadTransaction(transferId).second)
    }
}
