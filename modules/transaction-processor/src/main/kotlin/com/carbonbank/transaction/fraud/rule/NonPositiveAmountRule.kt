package com.carbonbank.transaction.fraud.rule

import com.carbonbank.common.types.Money
import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import org.springframework.stereotype.Component

/**
 * Flags non-positive amounts. account-service already validates this on the
 * API, so a zero/negative amount reaching the processor means a malformed or
 * tampered message — scored high enough to land in CRITICAL on its own.
 */
@Component
class NonPositiveAmountRule(private val properties: FraudProperties) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? =
        if (transfer.amount <= Money.ZERO) {
            RiskSignal(
                ruleId = "non-positive-amount",
                score = properties.nonPositiveAmount.score,
                reason = "Amount ${transfer.amount.toBigDecimal()} must be positive",
            )
        } else {
            null
        }
}
