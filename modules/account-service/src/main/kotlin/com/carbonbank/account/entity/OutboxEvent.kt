package com.carbonbank.account.entity

import com.carbonbank.account.table.OutboxEvents
import com.carbonbank.common.persistence.BaseUUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class OutboxEvent(id: EntityID<UUID>) : BaseUUIDEntity(id, OutboxEvents) {
    companion object : UUIDEntityClass<OutboxEvent>(OutboxEvents)

    var aggregateType by OutboxEvents.aggregateType
    var aggregateId by OutboxEvents.aggregateId
    var eventType by OutboxEvents.eventType
    var destination by OutboxEvents.destination
    var payload by OutboxEvents.payload
    var attempts by OutboxEvents.attempts
    var publishedAt by OutboxEvents.publishedAt
}
