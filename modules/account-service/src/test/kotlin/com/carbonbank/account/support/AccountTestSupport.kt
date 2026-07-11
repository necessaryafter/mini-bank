package com.carbonbank.account.support

import com.carbonbank.account.outbox.publisher.EventPublisher
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records outbox publishes instead of hitting SQS, so integration tests can
 * assert what the [com.carbonbank.account.outbox.OutboxPoller] shipped without
 * a live LocalStack. Being `@Primary`, it replaces the real
 * [com.carbonbank.account.outbox.publisher.impl.SqsEventPublisher] in every test context — so
 * no test ever tries to reach the SQS endpoint.
 */
class RecordingEventPublisher : EventPublisher {
    val published = CopyOnWriteArrayList<Pair<String, String>>()

    override fun publish(destination: String, payload: String) {
        published += destination to payload
    }
}

/**
 * The single shared test configuration for every account-service `@SpringBootTest`.
 *
 * Keeping the context configuration byte-for-byte identical across all test
 * classes is load-bearing, not cosmetic: the test helpers drive Exposed through
 * raw `transaction { }` blocks, which resolve the database from a process-global
 * default. A second, differently-configured Spring context would register a
 * second Exposed `Database` and clobber that default, so a raw `transaction { }`
 * in one test class could silently run against another context's database. One
 * shared context means one database, so pair this with the exact same
 * `@SpringBootTest`/`@Import` on every test class.
 */
@TestConfiguration
class AccountTestConfig {

    @Bean
    @Primary
    fun recordingEventPublisher() = RecordingEventPublisher()
}
