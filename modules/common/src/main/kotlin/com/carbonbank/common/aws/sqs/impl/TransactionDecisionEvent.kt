package com.carbonbank.common.aws.sqs.impl

import com.carbonbank.common.aws.sqs.GenericEvent
import com.carbonbank.common.transaction.TransferDecision
import com.carbonbank.common.types.IdempotencyKey
import java.time.Instant
import java.util.UUID

/**
 * The processor's fraud verdict on a transfer, sent back to account-service to
 * apply (docs/adr/0003). Carries [score] and [reasons] only for auditing/logging
 * on the account side — the decision itself is [decision].
 */
class TransactionDecisionEvent(
    val transferId: UUID,
    val decision: TransferDecision,
    val score: Int,
    val reasons: List<String>,

    override val idempotencyKey: IdempotencyKey,
    override val timestamp: Instant,
) : GenericEvent
