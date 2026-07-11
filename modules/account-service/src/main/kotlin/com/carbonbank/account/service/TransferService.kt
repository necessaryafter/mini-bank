package com.carbonbank.account.service

import com.carbonbank.account.controller.dto.CreateTransferRequest
import com.carbonbank.account.controller.dto.TransferResponse
import com.carbonbank.account.entity.Account
import com.carbonbank.account.entity.Entry
import com.carbonbank.account.entity.Transaction
import com.carbonbank.account.idempotency.TransferIdempotencyService
import com.carbonbank.account.outbox.OutboxRecorder
import com.carbonbank.common.aws.sqs.impl.TransactionCreatedEvent
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.transaction.TransactionStatus
import com.carbonbank.common.transaction.TransactionType
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import com.carbonbank.common.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TransferService(
    private val idempotencyService: TransferIdempotencyService,
    private val outboxRecorder: OutboxRecorder,
) {

    private companion object {
        // Queue the transaction-processor listens on to validate transfers.
        const val VALIDATE_TRANSFER_QUEUE = "validate-transfer"
    }

    /**
     * Reserves the idempotency key in Redis before touching Postgres so
     * concurrent requests with the same key race for a single winner; the
     * loser reads the winner's transaction id back and returns its state
     * instead of creating a second transfer. If the winner fails before
     * committing (e.g. an account doesn't exist), the reservation is
     * released so the key isn't stuck pointing at a transfer that never
     * got created.
     */
    @Transactional
    fun createTransfer(
        sourceAccountId: UUID,
        request: CreateTransferRequest,
        idempotencyKey: IdempotencyKey,
        requestContext: RequestContext,
    ): TransferResponse {
        val transactionId = UUID.randomUUID()
        val reserved = idempotencyService.reserve(idempotencyKey, transactionId)
        if (!reserved) {
            val existingId = idempotencyService.transactionIdFor(idempotencyKey)
                ?: throw ApiException(HttpStatus.CONFLICT, "Idempotency key is already being processed, retry shortly")
            return toResponse(loadTransaction(existingId))
        }

        return try {
            val tx = buildTransfer(transactionId, sourceAccountId, request, idempotencyKey)

            // Same transaction as the Transaction/Entry inserts above - the
            // event can't be lost if the transfer commits, nor published if it
            // rolls back (see docs/adr/0006). The OutboxPoller ships it to SQS.
            outboxRecorder.record(
                destination = VALIDATE_TRANSFER_QUEUE,
                aggregateType = "Transaction",
                aggregateId = tx.id.value,
                event = TransactionCreatedEvent(
                    transferId = tx.id.value,
                    sourceAccountId = sourceAccountId,
                    destinationAccountId = request.destinationAccountId,
                    amount = Money.from(request.amount),
                    requestContext = requestContext,
                    idempotencyKey = idempotencyKey,
                    timestamp = Instant.now(),
                ),
            )
            toResponse(tx)
        } catch (ex: Exception) {
            idempotencyService.release(idempotencyKey)
            throw ex
        }
    }

    @Transactional
    fun findTransfer(transactionId: UUID): TransferResponse =
        toResponse(loadTransaction(transactionId))

    private fun buildTransfer(
        transactionId: UUID,
        sourceAccountId: UUID,
        request: CreateTransferRequest,
        idempotencyKey: IdempotencyKey,
    ): Transaction {
        if (sourceAccountId == request.destinationAccountId) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Source and destination accounts must differ")
        }

        val from = Account.findById(sourceAccountId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Account $sourceAccountId not found")
        val to = Account.findById(request.destinationAccountId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Account ${request.destinationAccountId} not found")

        val amount = Money.from(request.amount)
        val tx = Transaction.new(transactionId) {
            description = "Transfer from $sourceAccountId to ${request.destinationAccountId}"

            this.idempotencyKey = idempotencyKey
            status = TransactionStatus.PENDING
        }

        Entry.new {
            transaction = tx
            account = from
            entryType = TransactionType.DEBIT
            this.amount = amount
            status = EntryStatus.PENDING
        }
        Entry.new {
            transaction = tx
            account = to
            entryType = TransactionType.CREDIT
            this.amount = amount
            status = EntryStatus.PENDING
        }
        return tx
    }

    private fun loadTransaction(transactionId: UUID): Transaction =
        Transaction.findById(transactionId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Transfer $transactionId not found")

    private fun toResponse(tx: Transaction): TransferResponse {
        val entries = tx.entries.toList()
        val debit = entries.single { it.entryType == TransactionType.DEBIT }
        val credit = entries.single { it.entryType == TransactionType.CREDIT }

        return TransferResponse(
            id = tx.id.value,
            sourceAccountId = debit.account.id.value,
            destinationAccountId = credit.account.id.value,
            amount = debit.amount.toBigDecimal(),
            status = tx.status,
        )
    }
}
