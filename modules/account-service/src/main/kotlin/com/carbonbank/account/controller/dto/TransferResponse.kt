package com.carbonbank.account.controller.dto

import com.carbonbank.common.transaction.TransactionStatus
import java.math.BigDecimal
import java.util.UUID

data class TransferResponse(
    val id: UUID,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: BigDecimal,
    val status: TransactionStatus,
)
