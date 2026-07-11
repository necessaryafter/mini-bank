package com.carbonbank.transaction.fraud.rule

import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.RecipientBlocklist
import org.springframework.stereotype.Component

/**
 * Flags transfers to a destination account previously reported for fraud (a
 * known mule or scam account). Weighted heavily: a confirmed report is a much
 * stronger signal than the behavioral heuristics.
 */
@Component
class ReportedRecipientRule(
    private val blocklist: RecipientBlocklist,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? =
        if (blocklist.isReported(transfer.destinationAccountId)) {
            RiskSignal(
                ruleId = "reported-recipient",
                score = properties.reportedRecipient.score,
                reason = "Destination account ${transfer.destinationAccountId} was previously reported",
            )
        } else {
            null
        }
}
