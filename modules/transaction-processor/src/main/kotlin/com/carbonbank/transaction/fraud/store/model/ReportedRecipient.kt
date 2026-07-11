package com.carbonbank.transaction.fraud.store.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * Persistence detail of the blocklist: not exposed by the [com.carbonbank.
 * transaction.fraud.store.RecipientBlocklist] port, which only answers
 * isReported/report. Presence of the document is the signal; its id is the
 * reported account's id.
 */
@Document("reported_recipients")
data class ReportedRecipient(
    @Id val accountId: UUID,
    val reportedAt: Instant,
    val reason: String? = null,
)
