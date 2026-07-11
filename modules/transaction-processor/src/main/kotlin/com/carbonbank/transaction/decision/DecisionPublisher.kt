package com.carbonbank.transaction.decision

import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent

/**
 * Seam between the review flow and the messaging transport, so review can be
 * tested without a live SQS. Production wiring is [SqsDecisionPublisher].
 */
interface DecisionPublisher {
    fun publish(decision: TransactionDecisionEvent)
}
