package com.carbonbank.transaction.fraud.service

import com.carbonbank.common.aws.sqs.impl.TransactionCreatedEvent
import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent
import com.carbonbank.common.transaction.TransferDecision
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.transaction.decision.DecisionPublisher
import com.carbonbank.transaction.fraud.FraudEngine
import com.carbonbank.transaction.fraud.model.FraudEvaluation
import com.carbonbank.transaction.fraud.model.RiskBand
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.profile.AccountProfileUpdater
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Reviews one incoming transfer for fraud: runs the engine, updates the
 * account's profile, and ships the decision back to account-service. This is the
 * seam the SQS listener delegates to, so the review flow is testable without any
 * messaging.
 */
@Service
class TransferReviewService(
    private val engine: FraudEngine,
    private val profileUpdater: AccountProfileUpdater,
    private val decisionPublisher: DecisionPublisher,
) {

    private val logger = LoggerFactory.getLogger(TransferReviewService::class.java)

    fun review(event: TransactionCreatedEvent): FraudEvaluation {
        // Evaluate against the profile as it stands, then fold this transfer in.
        val transfer = event.toTransferUnderReview()
        val evaluation = engine.evaluate(transfer)

        profileUpdater.record(transfer)

        val decision = evaluation.toDecisionEvent(event.idempotencyKey)
        decisionPublisher.publish(decision)

        logger.info(
            "Transfer {} scored {} -> {} ({})",
            evaluation.transferId, evaluation.score, evaluation.band, decision.decision,
        )
        return evaluation
    }
}

private fun TransactionCreatedEvent.toTransferUnderReview() = TransferUnderReview(
    transferId = transferId,
    sourceAccountId = sourceAccountId,
    destinationAccountId = destinationAccountId,
    amount = amount,
    requestedAt = timestamp,
    context = requestContext,
)

private fun FraudEvaluation.toDecisionEvent(idempotencyKey: IdempotencyKey) = TransactionDecisionEvent(
    transferId = transferId,
    decision = band.toDecision(),
    score = score,
    reasons = signals.map { it.reason },
    idempotencyKey = idempotencyKey,
    timestamp = Instant.now(),
)

private fun RiskBand.toDecision() = when (this) {
    RiskBand.LOW, RiskBand.MEDIUM -> TransferDecision.APPROVED
    RiskBand.HIGH -> TransferDecision.UNDER_REVIEW
    RiskBand.CRITICAL -> TransferDecision.REJECTED
}
