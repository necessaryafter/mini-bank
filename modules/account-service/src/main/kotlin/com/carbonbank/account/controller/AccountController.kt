package com.carbonbank.account.controller

import com.carbonbank.account.controller.dto.AccountResponse
import com.carbonbank.account.controller.dto.CreateAccountRequest
import com.carbonbank.account.service.AccountService
import com.carbonbank.common.types.IdempotencyKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Accounts", description = "Open accounts and read their current balance")
class AccountController(
    private val accountService: AccountService,
) {

    @PostMapping("/accounts")
    @Operation(
        summary = "Open an account",
        description = "Creates an account with an optional opening balance (default zero). Idempotent " +
            "on the Idempotency-Key header: retrying with the same key returns the original account " +
            "instead of creating a duplicate.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Account created"),
        ApiResponse(responseCode = "400", description = "Malformed request or failed validation", content = []),
        ApiResponse(responseCode = "409", description = "Idempotency key is still being processed", content = []),
    )
    fun createAccount(
        @Parameter(
            description = "Client-generated UUID that makes the submission safe to retry; the same " +
                "key returns the original account instead of creating a duplicate.",
            required = true,
        )
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateAccountRequest,
    ): ResponseEntity<AccountResponse> {
        val response = accountService.createAccount(request, IdempotencyKey.fromUuid(idempotencyKey))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Read an account and its current balance")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Account found"),
        ApiResponse(responseCode = "404", description = "No account with this id", content = []),
    )
    fun getAccount(@PathVariable id: UUID): AccountResponse =
        accountService.getAccount(id)
}
