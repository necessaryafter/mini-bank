package com.carbonbank.transaction.fraud.model

import java.util.UUID

/**
 * The processor's full verdict on one transfer: the total risk [score], the
 * [band] it falls into, and every [RiskSignal] that contributed — kept for
 * auditing and for building the decision event sent back to account-service.
 */
data class FraudEvaluation(
    val transferId: UUID,
    val score: Int,
    val band: RiskBand,
    val signals: List<RiskSignal>,
) {
    val actions: List<FraudAction> get() = band.actions
}
