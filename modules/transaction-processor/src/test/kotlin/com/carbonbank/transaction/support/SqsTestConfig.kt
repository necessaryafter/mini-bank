package com.carbonbank.transaction.support

import io.awspring.cloud.sqs.operations.SqsTemplate
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Context tests disable SQS (no LocalStack wired), so nothing auto-configures an
 * SqsTemplate — yet SqsDecisionPublisher needs one to be constructed. Supply a
 * mock so the context wires without a real broker.
 */
@TestConfiguration(proxyBeanMethods = false)
class SqsTestConfig {

    @Bean
    fun sqsTemplate(): SqsTemplate = Mockito.mock(SqsTemplate::class.java)
}
