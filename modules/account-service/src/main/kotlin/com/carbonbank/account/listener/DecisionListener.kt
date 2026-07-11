package com.carbonbank.account.listener

import com.carbonbank.account.service.TransferDecisionService
import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Consumes the processor's fraud decisions and applies them. Excluded from the
 * "test" profile so the SQS container isn't started against a broker that the
 * account-service test contexts don't provide.
 */
@Component
@Profile("!test")
class DecisionListener(
    private val objectMapper: ObjectMapper,
    private val decisionService: TransferDecisionService,
) {

    @SqsListener(TRANSFER_DECISION_QUEUE)
    fun onDecision(payload: String) {
        val event = objectMapper.readValue(payload, TransactionDecisionEvent::class.java)
        decisionService.apply(event)
    }

    private companion object {
        const val TRANSFER_DECISION_QUEUE = "transfer-decision"
    }
}
