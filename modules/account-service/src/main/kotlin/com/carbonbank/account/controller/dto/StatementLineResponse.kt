package com.carbonbank.account.controller.dto

import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(description = "A single movement on the account: one side (debit or credit) of a ledger transaction.")
data class StatementLineResponse(
    @field:Schema(description = "Ledger entry id.")
    val entryId: UUID,

    @field:Schema(description = "Id of the transaction this entry belongs to; the transfer's two sides share it.")
    val transactionId: UUID,

    @field:Schema(description = "Whether this movement debited or credited the account.", example = "CREDIT")
    val type: TransactionType,

    @field:Schema(description = "Movement amount (always positive; the sign is conveyed by 'type').", example = "100.00")
    val amount: BigDecimal,

    @field:Schema(
        description = "Account balance right after this movement settled. Null for movements that " +
            "carry no running-balance cursor (e.g. the genesis side of an opening deposit).",
        example = "100.00",
    )
    val balanceAfter: BigDecimal?,

    @field:Schema(description = "Entry lifecycle status.", example = "POSTED")
    val status: EntryStatus,

    @field:Schema(description = "Human-readable description carried by the transaction.", example = "Opening balance")
    val description: String,

    @field:Schema(description = "When the entry was recorded.")
    val createdAt: Instant,

    @field:Schema(description = "Monotonic ledger cursor; pass the last one seen as 'cursor' to page further back.")
    val sequence: Long,
)
