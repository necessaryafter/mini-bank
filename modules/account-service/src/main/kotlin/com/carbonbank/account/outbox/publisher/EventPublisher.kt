package com.carbonbank.account.outbox.publisher

/**
 * Seam between [com.carbonbank.account.outbox.OutboxPoller] and the messaging transport, so the poller can be
 * exercised without a live SQS (floci) and so the AWS SDK type doesn't leak
 * into the polling logic. Production wiring is [com.carbonbank.account.outbox.publisher.impl.SqsEventPublisher].
 */
interface EventPublisher {
    fun publish(destination: String, payload: String)
}