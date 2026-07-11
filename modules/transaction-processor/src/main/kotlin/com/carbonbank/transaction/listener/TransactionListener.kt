package com.carbonbank.transaction.listener

import com.carbonbank.common.aws.sqs.impl.TransactionCreatedEvent
import com.carbonbank.transaction.fraud.service.TransferReviewService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class TransactionListener(
    private val objectMapper: ObjectMapper,
    private val reviewService: TransferReviewService,
) {

    // Deserialize with the app's own mapper (Kotlin module + the Jackson
    // annotations on Money/IdempotencyKey) instead of trusting spring-cloud-aws's
    // converter to be configured identically to what account-service serializes.
    @SqsListener(VALIDATE_TRANSFER_QUEUE)
    fun onTransferMessage(payload: String) {
        val event = objectMapper.readValue(
            payload, TransactionCreatedEvent::class.java)

        reviewService.review(event)
    }

    private companion object {
        const val VALIDATE_TRANSFER_QUEUE = "validate-transfer"
    }
}
