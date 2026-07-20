package com.carbonbank.account.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class CreateTransferRequest(
    @field:Schema(
        description = "Account credited by the transfer; must differ from the source account.",
        example = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
    )
    @field:NotNull
    val destinationAccountId: UUID,

    @field:Schema(description = "Amount to transfer, strictly greater than zero.", example = "150.00")
    @field:NotNull
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal,
)
