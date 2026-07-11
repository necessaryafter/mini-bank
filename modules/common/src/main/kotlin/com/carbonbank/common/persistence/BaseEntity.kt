package com.carbonbank.common.persistence

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.UUID

/**
 * Base table for aggregate roots shared by every service module.
 * Centralizing id generation and audit columns keeps table
 * definitions in each microservice small and consistent.
 */
abstract class BaseIdTable(name: String) : UUIDTable(name) {
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

/**
 * Base class for aggregate roots shared by every service module.
 * Exposed has no Hibernate-style dirty-checking listener to bump
 * [updatedAt] automatically, so mutating call sites must call [touch]
 * before the transaction commits.
 */
abstract class BaseUUIDEntity(id: EntityID<UUID>, table: BaseIdTable) : UUIDEntity(id) {
    var createdAt by table.createdAt
    var updatedAt by table.updatedAt

    fun touch() {
        updatedAt = Instant.now()
    }
}
