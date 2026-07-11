package com.carbonbank.transaction.fraud.model

/**
 * Risk tiers a transfer's total score maps into, each with the actions
 * account-service should take. Thresholds and actions mirror FRAUD_DETECTION.md.
 *
 * The band is the processor's *risk* verdict; it is orthogonal to whether the
 * source account has enough balance, which is decided later under the account
 * lock by account-service's TransferCaptureService (see docs/adr/0005).
 */
enum class RiskBand(
    private val maxScore: Int,
    val actions: List<FraudAction>,
) {
    LOW(20, listOf(FraudAction.APPROVE)),
    MEDIUM(50, listOf(FraudAction.APPROVE, FraudAction.EMIT_MONITORING_EVENT)),
    HIGH(80, listOf(FraudAction.MANUAL_REVIEW)),
    CRITICAL(Int.MAX_VALUE, listOf(FraudAction.BLOCK, FraudAction.FREEZE_BALANCE, FraudAction.EMIT_ALERT)),
    ;

    companion object {
        /**
         * Maps a total score to its band. Scores at or below a band's ceiling
         * fall into the first matching band, so 0..20 → LOW, 21..50 → MEDIUM,
         * 51..80 → HIGH, 81+ → CRITICAL. Negative totals clamp to LOW.
         */
        fun fromScore(score: Int): RiskBand = entries.first { score <= it.maxScore }
    }
}
