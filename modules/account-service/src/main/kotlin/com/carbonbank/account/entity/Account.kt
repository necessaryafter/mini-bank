package com.carbonbank.account.entity

import com.carbonbank.account.table.Accounts
import com.carbonbank.account.table.Entries
import com.carbonbank.common.persistence.BaseUUIDEntity
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.types.Money
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class Account(id: EntityID<UUID>) : BaseUUIDEntity(id, Accounts) {
    companion object : UUIDEntityClass<Account>(Accounts)

    var ownerName by Accounts.ownerName
    val entries by Entry referrersOn Entries.account

    /**
     * The account never stores a balance column — it's always the
     * [Entries.balanceAfter] of the most recent POSTED entry, so posting a new
     * entry is the only place that can get it wrong instead of every reader
     * having to replay the whole history.
     */
    fun currentBalance(): Money =
        Entries
            .selectAll()
            .andWhere { Entries.account eq id }
            .andWhere { Entries.status eq EntryStatus.POSTED }
            .orderBy(Entries.sequence, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(Entries.balanceAfter)
            ?: Money.ZERO
}
