package com.carbonbank.account.outbox.publisher.impl

import com.carbonbank.account.outbox.publisher.EventPublisher
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.stereotype.Component

/**
 * Publishes an already-serialized JSON [payload] as the raw body of an SQS
 * message. The queue is created on first send (spring-cloud-aws default
 * QueueNotFoundStrategy.CREATE), so local dev needs no queue provisioning step.
 */
@Component
class SqsEventPublisher(
    private val sqsTemplate: SqsTemplate,
) : EventPublisher {

    override fun publish(destination: String, payload: String) {
        sqsTemplate.send(destination, payload)
    }
}