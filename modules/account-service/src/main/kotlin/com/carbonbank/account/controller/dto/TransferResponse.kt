package com.carbonbank.account.controller.dto

import com.carbonbank.common.transaction.TransactionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "A submitted transfer and its current processing status.")
data class TransferResponse(
    @field:Schema(description = "Transfer id; use it to poll GET /transfers/{id}.")
    val id: UUID,

    @field:Schema(description = "Account debited by the transfer.")
    val sourceAccountId: UUID,

    @field:Schema(description = "Account credited by the transfer.")
    val destinationAccountId: UUID,

    @field:Schema(description = "Transferred amount.", example = "150.00")
    val amount: BigDecimal,

    @field:Schema(description = "Lifecycle status; PENDING until the processor reaches a decision.")
    val status: TransactionStatus,
)
