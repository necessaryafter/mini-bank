package com.carbonbank.account.controller.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class CreateTransferRequest(
    @field:NotNull
    val destinationAccountId: UUID,

    @field:NotNull
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal,
)
