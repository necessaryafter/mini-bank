package com.carbonbank.transaction.decision

import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Sends the decision straight to SQS. Unlike account-service's outbox, this is a
 * direct best-effort send with no local durability — the processor has no
 * database of its own for an outbox, and the account-service consumer is
 * idempotent, so an occasional redelivery is safe (docs/adr/0006).
 */
@Component
class SqsDecisionPublisher(
    private val sqsTemplate: SqsTemplate,
    private val objectMapper: ObjectMapper,
) : DecisionPublisher {

    override fun publish(decision: TransactionDecisionEvent) {
        sqsTemplate.send(TRANSFER_DECISION_QUEUE, objectMapper.writeValueAsString(decision))
    }

    private companion object {
        const val TRANSFER_DECISION_QUEUE = "transfer-decision"
    }
}
