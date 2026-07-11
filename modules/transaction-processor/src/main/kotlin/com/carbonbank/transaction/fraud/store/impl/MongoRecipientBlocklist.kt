package com.carbonbank.transaction.fraud.store.impl

import com.carbonbank.transaction.fraud.store.RecipientBlocklist
import com.carbonbank.transaction.fraud.store.model.ReportedRecipient
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class MongoRecipientBlocklist(
    private val repository: ReportedRecipientRepository,
) : RecipientBlocklist {

    override fun isReported(accountId: UUID): Boolean =
        repository.existsById(accountId)

    override fun report(accountId: UUID, reason: String?) {
        repository.save(ReportedRecipient(accountId, Instant.now(), reason))
    }
}
