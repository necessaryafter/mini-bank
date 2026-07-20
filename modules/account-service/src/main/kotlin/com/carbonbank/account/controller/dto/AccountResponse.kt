package com.carbonbank.account.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "An account and its current balance.")
data class AccountResponse(
    @field:Schema(description = "Account id.")
    val id: UUID,

    @field:Schema(description = "Name of the account owner.", example = "Ada Lovelace")
    val ownerName: String,

    @field:Schema(description = "Current balance, derived from the account's posted ledger entries.", example = "100.00")
    val balance: BigDecimal,
)
