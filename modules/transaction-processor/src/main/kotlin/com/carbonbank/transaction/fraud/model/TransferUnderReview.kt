package com.carbonbank.transaction.fraud.model

import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.types.Money
import java.time.Instant
import java.util.UUID

/**
 * Everything the fraud rules reason about for one transfer. Built from the SQS
 * event payload only — it never carries account balances or the ledger, since
 * the processor never reads account-service's database (see docs/adr/0003).
 *
 * [context] holds the request-time signals used by the behavioural rules
 * (device, network, geo). It is nullable because older events, replays, or
 * server-to-server flows may not have captured it; rules that need it must
 * treat its absence as "cannot evaluate" and stay silent rather than assume
 * innocence or guilt.
 */
data class TransferUnderReview(
    val transferId: UUID,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: Money,
    val requestedAt: Instant,
    val context: RequestContext?,
)
