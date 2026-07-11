package com.carbonbank.account.outbox

import com.carbonbank.account.entity.OutboxEvent
import com.carbonbank.common.aws.sqs.GenericEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Appends an event to the outbox as part of the *caller's* transaction — no
 * separate commit — so the event lands atomically with the business change
 * that produced it, closing the dual-write gap between "row committed" and
 * "message published" (see docs/adr/0006). The [OutboxPoller] ships it to SQS
 * afterwards.
 *
 * Must be called from within an open Exposed transaction (i.e. a
 * `@Transactional` service method); it does not open one of its own.
 */
@Component
class OutboxRecorder(
    private val objectMapper: ObjectMapper,
) {

    fun record(destination: String, aggregateType: String, aggregateId: UUID, event: GenericEvent) {
        OutboxEvent.new {
            this.aggregateType = aggregateType
            this.aggregateId = aggregateId
            this.eventType = event::class.simpleName ?: event::class.java.name
            this.destination = destination
            this.payload = objectMapper.writeValueAsString(event)
        }
    }
}
