package com.carbonbank.account.entity

import com.carbonbank.account.table.Entries
import com.carbonbank.account.table.Transactions
import com.carbonbank.common.persistence.BaseUUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class Transaction(id: EntityID<UUID>) : BaseUUIDEntity(id, Transactions) {
    companion object : UUIDEntityClass<Transaction>(Transactions)

    var description by Transactions.description
    var idempotencyKey by Transactions.idempotencyKey
    var status by Transactions.status
    var reviewReason by Transactions.reviewReason

    val entries by Entry referrersOn Entries.transaction
}
