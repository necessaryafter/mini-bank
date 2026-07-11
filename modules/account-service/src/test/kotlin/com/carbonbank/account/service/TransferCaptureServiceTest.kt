package com.carbonbank.account.service

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
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Context configuration is intentionally identical to every other
// account-service @SpringBootTest — see AccountTestConfig for why.
@SpringBootTest(properties = ["outbox.poll-interval-ms=3600000", "outbox.initial-delay-ms=3600000"])
@Import(PostgresTestcontainer::class, RedisTestcontainer::class, AccountTestConfig::class)
@ActiveProfiles("test")
class TransferCaptureServiceTest {

    @Autowired
    lateinit var captureService: TransferCaptureService

    private fun newAccount(): UUID = transaction {
        Account.new { ownerName = "Test" }.id.value
    }

    /** Seeds a starting balance directly as an already-POSTED entry, bypassing [TransferCaptureService]. */
    private fun seedBalance(accountId: UUID, amount: Money) {
        transaction {
            val account = Account.findById(accountId)!!
            val opening = Transaction.new {
                description = "Opening balance"
                idempotencyKey = IdempotencyKey.fromUuid(UUID.randomUUID().toString())
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
    }

    private fun pendingTransfer(fromId: UUID, toId: UUID, amount: Money): UUID = transaction {
        val from = Account.findById(fromId)!!
        val to = Account.findById(toId)!!
        val tx = Transaction.new {
            description = "Transfer"
            idempotencyKey = IdempotencyKey.fromUuid(UUID.randomUUID().toString())
            status = TransactionStatus.PENDING
        }
        Entry.new {
            this.transaction = tx
            this.account = from
            entryType = TransactionType.DEBIT
            this.amount = amount
            status = EntryStatus.PENDING
        }
        Entry.new {
            this.transaction = tx
            this.account = to
            entryType = TransactionType.CREDIT
            this.amount = amount
            status = EntryStatus.PENDING
        }
        tx.id.value
    }

    private fun balanceOf(accountId: UUID): Money = transaction {
        Account.findById(accountId)!!.currentBalance()
    }

    private fun statusOf(transactionId: UUID): TransactionStatus = transaction {
        Transaction.findById(transactionId)!!.status
    }

    private fun entryStatusesOf(transactionId: UUID): List<EntryStatus> = transaction {
        Transaction.findById(transactionId)!!.entries.map { it.status }
    }

    @Test
    fun `captures a transfer with sufficient balance`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))

        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))
        captureService.capture(transferId)

        assertEquals(TransactionStatus.COMPLETED, statusOf(transferId))
        assertEquals(listOf(EntryStatus.POSTED, EntryStatus.POSTED), entryStatusesOf(transferId))
        assertEquals(Money.fromCents(7_000), balanceOf(from))
        assertEquals(Money.fromCents(3_000), balanceOf(to))
    }

    @Test
    fun `voids the entries and fails the transaction when balance is insufficient`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(1_000))

        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))
        captureService.capture(transferId)

        assertEquals(TransactionStatus.FAILED, statusOf(transferId))
        assertEquals(listOf(EntryStatus.VOIDED, EntryStatus.VOIDED), entryStatusesOf(transferId))
        assertEquals(Money.fromCents(1_000), balanceOf(from))
        assertEquals(Money.ZERO, balanceOf(to))
    }

    @Test
    fun `capturing an already finalized transaction is a no-op`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))

        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))
        captureService.capture(transferId)
        captureService.capture(transferId)

        assertEquals(TransactionStatus.COMPLETED, statusOf(transferId))
        assertEquals(Money.fromCents(7_000), balanceOf(from))
        assertEquals(Money.fromCents(3_000), balanceOf(to))
    }

    @Test
    fun `redelivering the same transaction concurrently captures it only once`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(10_000))

        val transferId = pendingTransfer(from, to, Money.fromCents(3_000))

        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        val runs = List(2) {
            pool.submit {
                barrier.await()
                captureService.capture(transferId)
            }
        }
        runs.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(TransactionStatus.COMPLETED, statusOf(transferId))
        assertEquals(Money.fromCents(7_000), balanceOf(from))
        assertEquals(Money.fromCents(3_000), balanceOf(to))
    }

    @Test
    fun `balance exactly equal to the transfer amount succeeds`() {
        val from = newAccount()
        val to = newAccount()
        seedBalance(from, Money.fromCents(5_000))

        val transferId = pendingTransfer(from, to, Money.fromCents(5_000))
        captureService.capture(transferId)

        assertEquals(TransactionStatus.COMPLETED, statusOf(transferId))
        assertEquals(Money.ZERO, balanceOf(from))
        assertEquals(Money.fromCents(5_000), balanceOf(to))
    }

    @Test
    fun `concurrent transfers in opposite directions never deadlock`() {
        val accountA = newAccount()
        val accountB = newAccount()
        seedBalance(accountA, Money.fromCents(100_000))
        seedBalance(accountB, Money.fromCents(100_000))

        val transferCount = 20
        val transferIds = (1..transferCount).map { i ->
            if (i % 2 == 0) {
                pendingTransfer(accountA, accountB, Money.fromCents(100))
            } else {
                pendingTransfer(accountB, accountA, Money.fromCents(100))
            }
        }

        val pool = Executors.newFixedThreadPool(transferIds.size)
        val barrier = CyclicBarrier(transferIds.size)
        val runs = transferIds.map { id ->
            pool.submit {
                barrier.await()
                captureService.capture(id)
            }
        }
        runs.forEach { it.get(20, TimeUnit.SECONDS) }
        pool.shutdown()

        assertTrue(transferIds.all { statusOf(it) == TransactionStatus.COMPLETED })
        assertEquals(Money.fromCents(200_000), balanceOf(accountA) + balanceOf(accountB))
    }
}
