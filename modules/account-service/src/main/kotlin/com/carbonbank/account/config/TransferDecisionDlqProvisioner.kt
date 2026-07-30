package com.carbonbank.account.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

/**
 * Provisions a dead-letter queue for `transfer-decision` and wires a redrive
 * policy onto the source queue (RF05): a message that keeps failing
 * [com.carbonbank.account.listener.DecisionListener] is retried up to
 * [maxReceiveCount] times, then SQS itself (not application code) moves it to
 * the DLQ, so one poison message can't block the rest of the queue (docs/adr/0013).
 *
 * Mirrors `S3StatementStore.ensureBucket`: floci boots empty with no
 * provisioning script, so this runs best-effort on every boot instead.
 * CreateQueue/SetQueueAttributes are both idempotent, so this converges to the
 * same state regardless of how many times it runs or whether
 * transaction-processor's own auto-create of this same queue (via
 * `SqsTemplate`) wins the race to create it first. Failures are only warned: a
 * context with no reachable broker (most tests) must still start.
 *
 * [SqsAsyncClient] is injected via [ObjectProvider], not directly, for the same
 * reason [S3StatementStore][com.carbonbank.account.statement.S3StatementStore]
 * tolerates a missing bucket: this app's own test contexts don't run against a
 * real broker either, so the bean may not resolve cleanly, and a hard
 * constructor dependency would turn a best-effort bootstrap into a required one.
 */
@Component
class TransferDecisionDlqProvisioner(
    private val sqsAsyncClient: ObjectProvider<SqsAsyncClient>,
    @param:Value($$"${carbonbank.sqs.transfer-decision.max-receive-count:5}") private val maxReceiveCount: Int,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun ensureDlq() {
        val client = sqsAsyncClient.ifAvailable ?: return

        runCatching {
            val dlqUrl = client.createQueue { it.queueName(DLQ_NAME) }.get().queueUrl()
            val dlqArn = client.getQueueAttributes {
                it.queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN)
            }.get().attributes()[QueueAttributeName.QUEUE_ARN]

            val queueUrl = client.createQueue { it.queueName(QUEUE_NAME) }.get().queueUrl()
            client.setQueueAttributes {
                it.queueUrl(queueUrl).attributes(
                    mapOf(
                        QueueAttributeName.REDRIVE_POLICY to
                            """{"deadLetterTargetArn":"$dlqArn","maxReceiveCount":"$maxReceiveCount"}""",
                    ),
                )
            }.get()
        }.onFailure {
            log.warn("Could not provision DLQ/redrive policy for '{}': {}", QUEUE_NAME, it.message)
        }
    }

    private companion object {
        const val QUEUE_NAME = "transfer-decision"
        const val DLQ_NAME = "transfer-decision-dlq"
    }
}
