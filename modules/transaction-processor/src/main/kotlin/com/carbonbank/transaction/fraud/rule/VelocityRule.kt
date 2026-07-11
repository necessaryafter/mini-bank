package com.carbonbank.transaction.fraud.rule

import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.VelocityStore
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Flags accounts firing off transfers faster than a human normally would — the
 * signature of a drained account or a script. The current transfer is counted
 * as part of the window, so the very Nth+1 rapid transfer trips the rule.
 */
@Component
class VelocityRule(
    private val velocity: VelocityStore,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? {
        val window = Duration.ofSeconds(properties.velocity.windowSeconds)
        val count = velocity.recordAndCount(transfer.sourceAccountId, window)

        return if (count > properties.velocity.maxTransfers) {
            RiskSignal(
                ruleId = "velocity",
                score = properties.velocity.score,
                reason = "$count transfers in the last ${properties.velocity.windowSeconds}s (limit ${properties.velocity.maxTransfers})",
            )
        } else {
            null
        }
    }
}
