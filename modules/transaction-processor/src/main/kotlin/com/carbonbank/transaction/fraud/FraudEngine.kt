package com.carbonbank.transaction.fraud

import com.carbonbank.transaction.fraud.model.FraudEvaluation
import com.carbonbank.transaction.fraud.model.RiskBand
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Runs every [FraudRule] over a transfer, sums the [com.carbonbank.transaction.fraud.model.RiskSignal]
 * scores into a total, and maps that total to a [RiskBand] (and its actions).
 *
 * Spring injects the full list of [FraudRule] beans, so the active rule set is
 * whatever is on the classpath — new rules plug in without touching the engine.
 */
@Service
class FraudEngine(private val rules: List<FraudRule>) {

    private val logger = LoggerFactory.getLogger(FraudEngine::class.java)

    fun evaluate(transfer: TransferUnderReview): FraudEvaluation {
        val signals = rules.mapNotNull { it.evaluate(transfer) }
        val score = signals.sumOf { it.score }
        val band = RiskBand.fromScore(score)

        val evaluation = FraudEvaluation(transfer.transferId, score, band, signals)
        if (band != RiskBand.LOW) {
            logger.warn(
                "Transfer {} scored {} ({}), signals: {}",
                transfer.transferId, score, band, signals.map { it.ruleId },
            )
        }

        return evaluation
    }
}
