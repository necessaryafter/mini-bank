package com.carbonbank.account.outbox

import com.carbonbank.account.controller.dto.CreateTransferRequest
import com.carbonbank.account.entity.Account
import com.carbonbank.account.service.TransferService
import com.carbonbank.account.support.AccountTestConfig
import com.carbonbank.account.support.RecordingEventPublisher
import com.carbonbank.account.table.OutboxEvents
import com.carbonbank.common.aws.sqs.impl.TransactionCreatedEvent
import com.carbonbank.common.testcontainers.PostgresTestcontainer
import com.carbonbank.common.testcontainers.RedisTestcontainer
import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.types.IdempotencyKey
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID

/**
 * Exercises the outbox end to end against a real Postgres, with the SQS send
 * itself stubbed by [RecordingEventPublisher] — the actual network call is
 * spring-cloud-aws's responsibility, so what matters here is that the event is
 * recorded transactionally with the transfer, that the poller ships exactly the
 * pending rows and stamps them, and that the payload round-trips back into the
 * domain event.
 */
// Context configuration is intentionally identical to every other
// account-service @SpringBootTest — see AccountTestConfig for why.
@SpringBootTest(properties = ["outbox.poll-interval-ms=3600000", "outbox.initial-delay-ms=3600000"])
@Import(PostgresTestcontainer::class, RedisTestcontainer::class, AccountTestConfig::class)
@ActiveProfiles("test")
class OutboxIntegrationTest {

    @Autowired
    lateinit var transferService: TransferService

    @Autowired
    lateinit var outboxPoller: OutboxPoller

    @Autowired
    lateinit var publisher: RecordingEventPublisher

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun newAccount(): UUID = transaction {
        Account.new { ownerName = "Test" }.id.value
    }

    @Test
    fun `records the event with the transfer and the poller publishes it once`() {
        val from = newAccount()
        val to = newAccount()
        val before = publisher.published.size

        val response = transferService.createTransfer(
            sourceAccountId = from,
            request = CreateTransferRequest(destinationAccountId = to, amount = BigDecimal("30.00")),
            idempotencyKey = IdempotencyKey.random(),
            requestContext = RequestContext(
                deviceId = "device-1",
                ipAddress = "203.0.113.7",
                country = "BR",
                latitude = -19.9,
                longitude = -43.9,
            ),
        )

        // Recorded transactionally with the transfer, not yet published.
        transaction {
            val row = OutboxEvents.selectAll()
                .andWhere { OutboxEvents.aggregateId eq response.id }
                .single()
            assertEquals("validate-transfer", row[OutboxEvents.destination])
            assertEquals("TransactionCreatedEvent", row[OutboxEvents.eventType])
            assertNull(row[OutboxEvents.publishedAt])
        }

        outboxPoller.publishPending()

        // Published exactly once, to the right queue...
        val mine = publisher.published.drop(before)
        assertEquals(1, mine.size)
        val (destination, payload) = mine.single()
        assertEquals("validate-transfer", destination)

        // ...the payload round-trips back into the domain event (proves the
        // Money/IdempotencyKey value classes serialize as clean scalars)...
        val event = objectMapper.readValue(payload, TransactionCreatedEvent::class.java)
        assertEquals(response.id, event.transferId)
        assertEquals(from, event.sourceAccountId)
        assertEquals(to, event.destinationAccountId)
        assertEquals(3_000, event.amount.cents)

        // ...including the request context that feeds the processor's fraud rules.
        assertEquals("device-1", event.requestContext?.deviceId)
        assertEquals("203.0.113.7", event.requestContext?.ipAddress)
        assertEquals("BR", event.requestContext?.country)
        assertEquals(-19.9, event.requestContext?.latitude)

        // ...and the row is stamped so a second poll ships nothing more.
        transaction {
            val row = OutboxEvents.selectAll()
                .andWhere { OutboxEvents.aggregateId eq response.id }
                .single()
            assertNotNull(row[OutboxEvents.publishedAt])
        }

        outboxPoller.publishPending()
        assertTrue(publisher.published.drop(before).size == 1)
    }
}
