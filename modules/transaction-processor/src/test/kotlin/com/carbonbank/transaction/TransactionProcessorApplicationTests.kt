package com.carbonbank.transaction

import com.carbonbank.common.testcontainers.MongoTestcontainer
import com.carbonbank.common.testcontainers.RedisTestcontainer
import com.carbonbank.transaction.support.SqsTestConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Boots the processor context with its own fraud datastore — Redis and Mongo —
 * proving the wiring from docs/adr/0007 comes up cleanly. SQS is disabled here:
 * the message plumbing is exercised by the listener tests, not this one.
 */
@SpringBootTest(properties = ["spring.cloud.aws.sqs.enabled=false"])
@Import(RedisTestcontainer::class, MongoTestcontainer::class, SqsTestConfig::class)
class TransactionProcessorApplicationTests {

    @Test
    fun contextLoads() {
    }
}
