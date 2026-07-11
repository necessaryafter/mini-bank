package com.carbonbank.transaction.fraud.model

/**
 * A rule's contribution to a transfer's risk. Rules return `null` rather than a
 * zero-score signal when they don't fire, so the engine only sums real hits.
 */
data class RiskSignal(
    val ruleId: String,
    val score: Int,
    val reason: String,
)
