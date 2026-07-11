package com.carbonbank.transaction.fraud.store.impl

import com.carbonbank.transaction.fraud.store.model.ReportedRecipient
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.UUID

interface ReportedRecipientRepository : MongoRepository<ReportedRecipient, UUID>
