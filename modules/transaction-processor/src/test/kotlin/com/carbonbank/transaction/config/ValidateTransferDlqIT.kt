package com.carbonbank.transaction.config

import io.floci.testcontainers.FlociContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.net.URI

/**
 * Proves RF05 end to end against a real (floci) SQS: a message that keeps
 * failing redelivery past `validate-transfer`'s maxReceiveCount is moved to
 * `validate-transfer-dlq` by SQS itself, and stops being handed back by the
 * source queue. Deliberately not a @SpringBootTest, same reasoning as
 * S3PresignRoundTripIT: this only needs [ValidateTransferDlqProvisioner] and a
 * plain SDK client, not the whole processor context.
 */
@Testcontainers
class ValidateTransferDlqIT {

    @Test
    fun `poison message is moved to the DLQ after maxReceiveCount deliveries`() {
        val async = sqsAsyncClient()
        val sync = sqsClient()

        ValidateTransferDlqProvisioner(fixedProvider(async), maxReceiveCount = MAX_RECEIVE_COUNT).ensureDlq()

        val queueUrl = sync.getQueueUrl { it.queueName("validate-transfer") }.queueUrl()
        val dlqUrl = sync.getQueueUrl { it.queueName("validate-transfer-dlq") }.queueUrl()

        // Short visibility timeout so the test doesn't have to wait long for the
        // message to become receivable again between "processing failed" attempts.
        sync.setQueueAttributes {
            it.queueUrl(queueUrl).attributes(mapOf(QueueAttributeName.VISIBILITY_TIMEOUT to "1"))
        }

        sync.sendMessage { it.queueUrl(queueUrl).messageBody("poison") }

        // Receive without ever deleting: each receive bumps SQS's own
        // ApproximateReceiveCount, and once it exceeds the redrive policy's
        // threshold the message is routed to the DLQ instead of being handed
        // back. One extra iteration over maxReceiveCount as headroom for
        // exactly which receive attempt the broker performs the move on.
        repeat(MAX_RECEIVE_COUNT + 2) {
            Thread.sleep(1_200)
            sync.receiveMessage { it.queueUrl(queueUrl).maxNumberOfMessages(1).waitTimeSeconds(1) }
        }

        val fromSource = sync.receiveMessage { it.queueUrl(queueUrl).maxNumberOfMessages(1).waitTimeSeconds(1) }
            .messages()
        assertEquals(0, fromSource.size, "poison message must stop being redelivered on the source queue")

        val fromDlq = sync.receiveMessage { it.queueUrl(dlqUrl).maxNumberOfMessages(1).waitTimeSeconds(5) }
            .messages()
        assertEquals(1, fromDlq.size, "poison message must have landed on the DLQ")
        assertEquals("poison", fromDlq.single().body())
    }

    private fun fixedProvider(client: SqsAsyncClient) = object : ObjectProvider<SqsAsyncClient> {
        override fun getIfAvailable() = client
    }

    private fun sqsAsyncClient(): SqsAsyncClient = SqsAsyncClient.builder()
        .endpointOverride(URI.create(floci.getEndpoint()))
        .credentialsProvider(credentials())
        .region(Region.of(floci.region))
        .build()

    private fun sqsClient(): SqsClient = SqsClient.builder()
        .endpointOverride(URI.create(floci.getEndpoint()))
        .credentialsProvider(credentials())
        .region(Region.of(floci.region))
        .build()

    private fun credentials() =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(floci.accessKey, floci.secretKey))

    companion object {
        private const val MAX_RECEIVE_COUNT = 2

        // floci, this project's AWS emulator (see compose.yaml and
        // FlociTestcontainer), not the official localstack/localstack image.
        // Starts with every service disabled, then re-enables only SQS.
        @Container
        @JvmStatic
        val floci: FlociContainer = FlociContainer().disableAllServices().withSqsConfig { it.enabled(true) }
    }
}
