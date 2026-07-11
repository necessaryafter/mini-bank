package com.carbonbank.account.table

import com.carbonbank.common.persistence.BaseIdTable
import com.carbonbank.common.persistence.idempotencyKey
import com.carbonbank.common.transaction.TransactionStatus

object Transactions : BaseIdTable("transactions") {
    val description = varchar("description", 255)
    val idempotencyKey = idempotencyKey("idempotency_key").uniqueIndex()
    val status = enumerationByName("status", 10, TransactionStatus::class).default(TransactionStatus.PENDING)
    val reviewReason = text("review_reason").nullable()
}
