package com.carbonbank.transaction.fraud.store.impl

import com.carbonbank.transaction.fraud.store.AccountProfileStore
import com.carbonbank.transaction.fraud.store.model.AccountProfile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MongoAccountProfileStore(
    private val repository: AccountProfileRepository,
) : AccountProfileStore {

    override fun find(accountId: UUID): AccountProfile? =
        repository.findById(accountId).orElse(null)

    override fun save(profile: AccountProfile): AccountProfile =
        repository.save(profile)
}
