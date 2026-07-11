package com.carbonbank.common.aws.sqs.impl

import com.carbonbank.common.aws.sqs.GenericEvent
import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import java.time.Instant
import java.util.UUID

class TransactionCreatedEvent(
    val transferId: UUID,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: Money,

    // Request-time signals for the processor's fraud rules. Nullable so older
    // events, replays, or clients that omit the headers still deserialize.
    val requestContext: RequestContext? = null,

    override val idempotencyKey: IdempotencyKey,
    override val timestamp: Instant,
) : GenericEvent
