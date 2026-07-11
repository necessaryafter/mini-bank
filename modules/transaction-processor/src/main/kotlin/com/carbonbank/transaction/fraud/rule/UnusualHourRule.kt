package com.carbonbank.transaction.fraud.rule

import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import org.springframework.stereotype.Component
import java.time.ZoneOffset

/**
 * Flags transfers at an hour the account has never transacted in before (e.g. a
 * user who only ever moves money 07h–21h suddenly sending at 3am). Hours are
 * compared in UTC; a production system would use the account's own timezone.
 */
@Component
class UnusualHourRule(
    private val profiles: AccountProfileStore,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? {
        val profile = profiles.find(transfer.sourceAccountId) ?: return null
        if (profile.transferCount < properties.unusualHour.minSamples) return null

        val hour = transfer.requestedAt.atZone(ZoneOffset.UTC).hour
        if (hour in profile.activeHours) return null

        return RiskSignal(
            ruleId = "unusual-hour",
            score = properties.unusualHour.score,
            reason = "Transfer at ${hour}h UTC, outside the account's usual hours ${profile.activeHours.sorted()}",
        )
    }
}
