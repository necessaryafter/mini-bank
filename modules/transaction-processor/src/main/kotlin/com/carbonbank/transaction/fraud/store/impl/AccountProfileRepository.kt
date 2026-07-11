package com.carbonbank.transaction.fraud.store.impl

import com.carbonbank.transaction.fraud.store.model.AccountProfile
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.UUID

interface AccountProfileRepository : MongoRepository<AccountProfile, UUID>
