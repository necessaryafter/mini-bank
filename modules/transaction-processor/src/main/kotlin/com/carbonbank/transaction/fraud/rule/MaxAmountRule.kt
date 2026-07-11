package com.carbonbank.transaction.fraud.rule

import com.carbonbank.common.types.Money
import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import org.springframework.stereotype.Component

/**
 * Flags transfers whose amount exceeds the configured per-transfer ceiling —
 * the classic "unusually large movement" signal in a digital wallet.
 */
@Component
class MaxAmountRule(private val properties: FraudProperties) : FraudRule {
    private val limit = Money.from(properties.maxAmount.threshold)

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? =
        if (transfer.amount > limit) {
            RiskSignal(
                ruleId = "max-amount",
                score = properties.maxAmount.score,
                reason = "Amount ${transfer.amount.toBigDecimal()} exceeds the per-transfer limit of ${limit.toBigDecimal()}",
            )
        } else {
            null
        }
}
