package com.carbonbank.transaction.fraud.store

import com.carbonbank.transaction.fraud.store.model.AccountProfile
import java.util.UUID

/**
 * Behind a narrow interface so the profile-based rules depend on this and not on
 * Spring Data directly — that keeps them unit-testable with an in-memory fake.
 */
interface AccountProfileStore {
    fun find(accountId: UUID): AccountProfile?
    fun save(profile: AccountProfile): AccountProfile
}
