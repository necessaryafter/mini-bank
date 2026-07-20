package com.carbonbank.account.table

import com.carbonbank.common.persistence.BaseIdTable
import com.carbonbank.common.persistence.idempotencyKeyNullable

object Accounts : BaseIdTable("accounts") {
    val ownerName = varchar("owner_name", 255)

    // Durable backstop for idempotent account creation (Redis is the fast path;
    // this unique index is what survives a cache flush). Nullable: the genesis
    // account and test-created accounts have no client key.
    val idempotencyKey = idempotencyKeyNullable("idempotency_key").uniqueIndex()
}
