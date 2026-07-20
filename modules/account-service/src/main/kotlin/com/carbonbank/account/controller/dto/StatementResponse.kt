package com.carbonbank.account.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "A page of an account's movement history, newest first.")
data class StatementResponse(
    @field:Schema(description = "Account the statement belongs to.")
    val accountId: UUID,

    @field:Schema(description = "Name of the account owner.", example = "Ada Lovelace")
    val ownerName: String,

    @field:Schema(description = "Current balance, derived from the account's posted ledger entries.", example = "60.00")
    val currentBalance: BigDecimal,

    @field:Schema(description = "Movements on this page, ordered newest first.")
    val lines: List<StatementLineResponse>,

    @field:Schema(
        description = "Cursor for the next (older) page: pass it as the 'cursor' query param. " +
            "Null when there are no older movements.",
    )
    val nextCursor: Long?,
)
