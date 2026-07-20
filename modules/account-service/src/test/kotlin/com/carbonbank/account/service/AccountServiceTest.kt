package com.carbonbank.account.service

import com.carbonbank.account.controller.dto.CreateAccountRequest
import com.carbonbank.account.entity.Account
import com.carbonbank.account.support.AccountTestConfig
import com.carbonbank.account.table.Accounts
import com.carbonbank.account.table.Entries
import com.carbonbank.common.testcontainers.PostgresTestcontainer
import com.carbonbank.common.testcontainers.RedisTestcontainer
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionType
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import com.carbonbank.common.web.ApiException
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// Context configuration is intentionally identical to every other
// account-service @SpringBootTest — see AccountTestConfig for why.
@SpringBootTest(properties = ["outbox.poll-interval-ms=3600000", "outbox.initial-delay-ms=3600000"])
@Import(PostgresTestcontainer::class, RedisTestcontainer::class, AccountTestConfig::class)
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    lateinit var accountService: AccountService

    private fun randomKey() = IdempotencyKey.random()

    private fun balanceOf(accountId: UUID): Money = transaction {
        Account.findById(accountId)!!.currentBalance()
    }

    private fun accountsWithKey(key: IdempotencyKey): Long = transaction {
        Accounts.selectAll().andWhere { Accounts.idempotencyKey eq key }.count()
    }

    /** Sums the cents of every POSTED entry of a type, across the whole ledger. */
    private fun postedTotal(type: TransactionType): Long = transaction {
        Entries.selectAll()
            .andWhere { Entries.status eq EntryStatus.POSTED }
            .andWhere { Entries.entryType eq type }
            .sumOf { it[Entries.amount].cents }
    }

    @Test
    fun `opens an account with zero balance by default`() {
        val response = accountService.createAccount(CreateAccountRequest(ownerName = "Ada"), randomKey())

        assertEquals("Ada", response.ownerName)
        assertEquals(BigDecimal("0.00"), response.balance)
        assertEquals(Money.ZERO, balanceOf(response.id))
    }

    @Test
    fun `opens an account with an initial balance and keeps the ledger balanced`() {
        val debitsBefore = postedTotal(TransactionType.DEBIT)
        val creditsBefore = postedTotal(TransactionType.CREDIT)

        val response = accountService.createAccount(
            CreateAccountRequest(ownerName = "Grace", initialBalance = BigDecimal("100.00")),
            randomKey(),
        )

        assertEquals(BigDecimal("100.00"), response.balance)
        assertEquals(Money.from(BigDecimal("100.00")), balanceOf(response.id))

        // The opening deposit is a real double-entry: the genesis debit offsets the
        // new account's credit, so the ledger's debit/credit totals move in lockstep.
        assertEquals(debitsBefore + 10_000, postedTotal(TransactionType.DEBIT))
        assertEquals(creditsBefore + 10_000, postedTotal(TransactionType.CREDIT))
        assertEquals(postedTotal(TransactionType.DEBIT), postedTotal(TransactionType.CREDIT))
    }

    @Test
    fun `reading an unknown account is a 404`() {
        val ex = assertThrows(ApiException::class.java) {
            accountService.getAccount(UUID.randomUUID())
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun `replaying the same idempotency key returns the original account`() {
        val key = randomKey()
        val request = CreateAccountRequest(ownerName = "Alan", initialBalance = BigDecimal("50.00"))

        val first = accountService.createAccount(request, key)
        val second = accountService.createAccount(request, key)

        assertEquals(first.id, second.id)
        assertEquals(1, accountsWithKey(key))
        // The opening balance is not credited twice on replay.
        assertEquals(Money.from(BigDecimal("50.00")), balanceOf(first.id))
    }

    @Test
    fun `two different keys create two distinct accounts`() {
        val a = accountService.createAccount(CreateAccountRequest(ownerName = "A"), randomKey())
        val b = accountService.createAccount(CreateAccountRequest(ownerName = "B"), randomKey())
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `concurrent creation with the same key creates only one account`() {
        val key = randomKey()
        val request = CreateAccountRequest(ownerName = "Concurrent", initialBalance = BigDecimal("10.00"))

        val successes = AtomicInteger(0)
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        val runs = List(2) {
            pool.submit {
                barrier.await()
                // The loser of the Redis race may throw (the winner's row is not
                // committed yet); the durable guarantee under test is that at most
                // one account row is ever created for the key.
                runCatching { accountService.createAccount(request, key) }
                    .onSuccess { successes.incrementAndGet() }
            }
        }
        runs.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, accountsWithKey(key))
        assertTrue(successes.get() >= 1, "at least one creation should succeed")
    }
}
