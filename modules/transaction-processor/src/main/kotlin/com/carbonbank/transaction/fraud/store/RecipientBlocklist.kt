package com.carbonbank.transaction.fraud.store

import java.util.UUID

/**
 * Behind a narrow interface for the same testability reason as
 * [AccountProfileStore].
 */
interface RecipientBlocklist {
    fun isReported(accountId: UUID): Boolean
    fun report(accountId: UUID, reason: String?)
}
