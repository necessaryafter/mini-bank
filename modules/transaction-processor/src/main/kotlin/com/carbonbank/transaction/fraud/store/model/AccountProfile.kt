package com.carbonbank.transaction.fraud.store.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * The learned behavioral baseline for one account, persisted in the processor's
 * own Mongo (see docs/adr/0007). It is folded forward from observed transfers,
 * so a cold profile (low [transferCount]) makes the profile-based rules abstain
 * rather than punish a user with no history.
 */
@Document("account_profiles")
data class AccountProfile(
    @Id val accountId: UUID,
    val transferCount: Long = 0,
    val amountSumCents: Long = 0,
    val knownDevices: Set<String> = emptySet(),
    val knownCountries: Set<String> = emptySet(),
    // UTC hours (0–23); UnusualHourRule compares the transfer's UTC hour to this.
    val activeHours: Set<Int> = emptySet(),
    // Last position and when it was seen; feeds ImpossibleTravelRule.
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastSeenAt: Instant? = null,
) {
    val averageAmountCents: Long
        get() = if (transferCount == 0L) 0 else amountSumCents / transferCount
}
