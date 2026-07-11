package com.carbonbank.transaction.fraud.store

import java.time.Duration
import java.util.UUID

/**
 * Behind an interface so VelocityRule can be unit-tested without Redis.
 */
interface VelocityStore {
    // Returns the count within [window] *including* the transfer it records now,
    // so the rule sees the current transfer reflected in the total.
    fun recordAndCount(accountId: UUID, window: Duration): Long
}
