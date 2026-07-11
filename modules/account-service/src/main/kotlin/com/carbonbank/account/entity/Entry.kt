package com.carbonbank.account.entity

import com.carbonbank.account.table.Entries
import com.carbonbank.common.persistence.BaseUUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class Entry(id: EntityID<UUID>) : BaseUUIDEntity(id, Entries) {
    companion object : UUIDEntityClass<Entry>(Entries)

    var transaction by Transaction referencedOn Entries.transaction
    var account by Account referencedOn Entries.account
    var entryType by Entries.entryType
    var amount by Entries.amount
    var status by Entries.status
    var balanceAfter by Entries.balanceAfter
    val sequence by Entries.sequence
}
