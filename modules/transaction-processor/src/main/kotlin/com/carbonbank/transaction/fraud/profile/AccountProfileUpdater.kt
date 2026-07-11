package com.carbonbank.transaction.fraud.profile

import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import com.carbonbank.transaction.fraud.store.model.AccountProfile
import org.springframework.stereotype.Component
import java.time.ZoneOffset

/**
 * Folds a processed transfer into the account's behavioural profile so the
 * profile-based rules learn from history. Must run *after* the rules evaluate
 * the transfer (see TransferReviewService) or the transfer would pollute its
 * own baseline.
 */
@Component
class AccountProfileUpdater(
    private val profiles: AccountProfileStore,
) {

    fun record(transfer: TransferUnderReview) {
        val base = profiles.find(transfer.sourceAccountId)
            ?: AccountProfile(transfer.sourceAccountId)

        val ctx = transfer.context
        val hour = transfer.requestedAt.atZone(ZoneOffset.UTC).hour

        // Position and time move together: bumping lastSeenAt while keeping a
        // stale position would make the next transfer's implied speed bogus, so
        // geo-less transfers leave the last-known location untouched.
        val hasGeo = ctx?.latitude != null && ctx.longitude != null

        profiles.save(
            base.copy(
                transferCount = base.transferCount + 1,
                amountSumCents = base.amountSumCents + transfer.amount.cents,
                knownDevices = base.knownDevices + listOfNotNull(ctx?.deviceId),
                knownCountries = base.knownCountries + listOfNotNull(ctx?.country),
                activeHours = base.activeHours + hour,
                lastLatitude = if (hasGeo) ctx.latitude else base.lastLatitude,
                lastLongitude = if (hasGeo) ctx.longitude else base.lastLongitude,
                lastSeenAt = if (hasGeo) transfer.requestedAt else base.lastSeenAt,
            ),
        )
    }
}
