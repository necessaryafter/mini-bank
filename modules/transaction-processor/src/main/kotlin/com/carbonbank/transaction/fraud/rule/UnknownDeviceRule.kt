package com.carbonbank.transaction.fraud.rule

import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import org.springframework.stereotype.Component

/**
 * Flags a transfer from a device the account has never used (e.g. a user who
 * always transacts from their Android suddenly appearing on an iPhone). Abstains
 * until the profile has at least one known device, so the account's first-ever
 * transfer isn't flagged against an empty baseline.
 */
@Component
class UnknownDeviceRule(
    private val profiles: AccountProfileStore,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? {
        val deviceId = transfer.context?.deviceId ?: return null
        val profile = profiles.find(transfer.sourceAccountId) ?: return null
        if (profile.knownDevices.isEmpty() || deviceId in profile.knownDevices) return null

        return RiskSignal(
            ruleId = "unknown-device",
            score = properties.unknownDevice.score,
            reason = "Transfer from unrecognised device $deviceId",
        )
    }
}
