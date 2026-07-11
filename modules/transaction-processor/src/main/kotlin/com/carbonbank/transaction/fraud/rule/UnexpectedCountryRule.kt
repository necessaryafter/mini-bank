package com.carbonbank.transaction.fraud.rule

import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import org.springframework.stereotype.Component

/**
 * Flags a transfer from a country the account has never transacted from (e.g. a
 * user who lives in Brazil suddenly transferring from Romania). Abstains until
 * the profile has at least one known country to compare against.
 */
@Component
class UnexpectedCountryRule(
    private val profiles: AccountProfileStore,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? {
        val country = transfer.context?.country ?: return null
        val profile = profiles.find(transfer.sourceAccountId) ?: return null
        if (profile.knownCountries.isEmpty() || country in profile.knownCountries) return null

        return RiskSignal(
            ruleId = "unexpected-country",
            score = properties.unexpectedCountry.score,
            reason = "Transfer from unexpected country $country, usual: ${profile.knownCountries}",
        )
    }
}
