package com.carbonbank.transaction.fraud

import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview

/**
 * A single fraud/risk check over a transfer. Every [FraudRule] bean is collected
 * and run by [FraudEngine], so adding a new check is just adding a new bean —
 * no wiring changes.
 *
 * Stateless rules read only the [TransferUnderReview]; stateful rules (velocity,
 * behavioural profile, blocklist) take their own store as a constructor
 * dependency — that store is owned solely by the processor and is never
 * account-service's database (see docs/adr/0003).
 */
fun interface FraudRule {

    /**
     * @return a [RiskSignal] if this rule fires, or `null` if the transfer
     * passes it (or the rule lacks the data to judge and chooses to abstain).
     */
    fun evaluate(transfer: TransferUnderReview): RiskSignal?
}
