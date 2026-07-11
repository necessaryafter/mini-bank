package com.carbonbank.account.table

import com.carbonbank.common.persistence.BaseIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object OutboxEvents : BaseIdTable("outbox_events") {
    val aggregateType = varchar("aggregate_type", 50)
    val aggregateId = uuid("aggregate_id")
    val eventType = varchar("event_type", 100)
    val destination = varchar("destination", 100)
    val payload = text("payload")
    val attempts = integer("attempts").default(0)

    /** Null until the poller ships this event to SQS. */
    val publishedAt = timestamp("published_at").nullable()
}
