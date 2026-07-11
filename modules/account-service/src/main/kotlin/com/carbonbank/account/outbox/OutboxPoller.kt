package com.carbonbank.account.outbox

import com.carbonbank.account.outbox.publisher.EventPublisher
import com.carbonbank.account.table.OutboxEvents
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The "publish" half of the outbox: reads a batch of not-yet-published events,
 * oldest first, and pushes each to its destination SQS queue, stamping
 * published_at on success. The "write" half happens transactionally with the
 * business change (see [OutboxRecorder]), so a crash between the DB commit and
 * the SQS send never loses an event — it's just picked up on the next poll.
 *
 * `FOR UPDATE SKIP LOCKED` lets multiple account-service instances poll at the
 * same time without double-publishing or blocking one another: each instance
 * claims a disjoint set of rows and skips the ones a peer already holds.
 * Delivery is therefore at-least-once (a send that succeeds just before a
 * rollback is re-sent later), which is safe because the consumer side is
 * idempotent (redelivery of the same transfer is a no-op — see docs/adr/0005).
 */
@Component
class OutboxPoller(
    private val eventPublisher: EventPublisher,
) {
    companion object {
        private const val BATCH_SIZE = 50
    }

    private val log = LoggerFactory.getLogger(OutboxPoller::class.java)

    @Scheduled(
        fixedDelayString = $$"${outbox.poll-interval-ms:1000}",
        initialDelayString = $$"${outbox.initial-delay-ms:0}",
    )
    @Transactional
    fun publishPending() {
        val claimed = OutboxEvents
            .selectAll()
            .andWhere { OutboxEvents.publishedAt.isNull() }
            .orderBy(OutboxEvents.createdAt, SortOrder.ASC)
            .limit(BATCH_SIZE)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .map {
                ClaimedEvent(
                    id = it[OutboxEvents.id].value,
                    destination = it[OutboxEvents.destination],
                    payload = it[OutboxEvents.payload],
                    attempts = it[OutboxEvents.attempts],
                )
            }

        for (event in claimed) {
            try {
                eventPublisher.publish(event.destination, event.payload)
                OutboxEvents.update({ OutboxEvents.id eq event.id }) {
                    it[publishedAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            } catch (ex: Exception) {
                // One bad message must not stall the rest of the batch; bump the
                // attempt counter and move on. The row stays unpublished, so the
                // next poll retries it.
                OutboxEvents.update({ OutboxEvents.id eq event.id }) {
                    it[attempts] = event.attempts + 1
                    it[updatedAt] = Instant.now()
                }

                log.warn(
                    "Failed to publish outbox event {} to {} (attempt {})",
                    event.id, event.destination, event.attempts + 1, ex,
                )
            }
        }
    }

    private data class ClaimedEvent(
        val id: UUID,
        val destination: String,
        val payload: String,
        val attempts: Int,
    )
}
