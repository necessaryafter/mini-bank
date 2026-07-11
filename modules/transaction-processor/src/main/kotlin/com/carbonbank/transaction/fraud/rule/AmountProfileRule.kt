package com.carbonbank.transaction.fraud.rule

import com.carbonbank.common.types.Money
import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import org.springframework.stereotype.Component

/**
 * Flags a transfer that dwarfs what the account usually sends (e.g. a user who
 * moves R$20–300 suddenly sending R$18.000). Unlike [MaxAmountRule]'s absolute
 * ceiling, this is relative to each account's own learned average, so it catches
 * amounts that are perfectly normal globally but wildly abnormal for this user.
 */
@Component
class AmountProfileRule(
    private val profiles: AccountProfileStore,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? {
        val profile = profiles.find(transfer.sourceAccountId) ?: return null
        if (profile.transferCount < properties.amountProfile.minSamples) return null

        val threshold = profile.averageAmountCents * properties.amountProfile.multiplier
        if (transfer.amount.cents <= threshold) return null

        val average = Money.fromCents(profile.averageAmountCents).toBigDecimal()
        return RiskSignal(
            ruleId = "amount-profile",
            score = properties.amountProfile.score,
            reason = "Amount ${transfer.amount.toBigDecimal()} is over ${properties.amountProfile.multiplier}x the account's usual average of $average",
        )
    }
}
