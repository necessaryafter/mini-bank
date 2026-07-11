package com.carbonbank.common.aws.sqs

import com.carbonbank.common.types.IdempotencyKey
import java.time.Instant

interface GenericEvent {
    val idempotencyKey: IdempotencyKey
    val timestamp: Instant
}