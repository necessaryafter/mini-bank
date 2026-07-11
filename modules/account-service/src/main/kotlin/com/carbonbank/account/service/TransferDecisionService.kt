package com.carbonbank.account.service

import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent
import com.carbonbank.common.transaction.TransferDecision
import org.springframework.stereotype.Service

/**
 * Applies the transaction-processor's fraud verdict to a transfer. Approved
 * transfers go through the balance-gated capture; rejected ones fail; held ones
 * stay PENDING (see docs/adr/0010). The [TransferCaptureService] methods it calls
 * are each idempotent, so a redelivered decision is safe.
 */
@Service
class TransferDecisionService(
    private val captureService: TransferCaptureService,
) {

    fun apply(event: TransactionDecisionEvent) {
        val reason = event.reasons.joinToString("; ")
        when (event.decision) {
            TransferDecision.APPROVED -> captureService.capture(event.transferId)
            TransferDecision.REJECTED -> captureService.reject(event.transferId, reason)
            TransferDecision.UNDER_REVIEW -> captureService.hold(event.transferId, reason)
        }
    }
}
