package com.carbonbank.account.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class CreateAccountRequest(
    @field:Schema(description = "Name of the account owner.", example = "Ada Lovelace")
    @field:NotBlank
    val ownerName: String,

    @field:Schema(
        description = "Opening balance, zero or more. A positive value is credited to the account " +
            "from the system issuer at creation time.",
        example = "100.00",
        defaultValue = "0.00",
    )
    @field:NotNull
    @field:DecimalMin(value = "0.00")
    val initialBalance: BigDecimal = BigDecimal.ZERO,
)
