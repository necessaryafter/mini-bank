package com.carbonbank.account.controller

import com.carbonbank.account.controller.dto.CreateTransferRequest
import com.carbonbank.account.controller.dto.TransferResponse
import com.carbonbank.account.service.TransferService
import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.types.IdempotencyKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
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
@Tag(name = "Transfers", description = "Submit money transfers and read their processing status")
class TransferController(
    private val transferService: TransferService,
) {

    @PostMapping("/accounts/{accountId}/transfers")
    @Operation(
        summary = "Submit a transfer from an account",
        description = "Accepts the transfer for asynchronous processing and returns 202 with its " +
            "initial PENDING status. The transfer settles through the fraud/decision pipeline; poll " +
            "GET /transfers/{id} for the terminal status.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Transfer accepted for processing"),
        ApiResponse(responseCode = "400", description = "Malformed request or failed validation", content = []),
        ApiResponse(responseCode = "404", description = "Source or destination account not found", content = []),
        ApiResponse(responseCode = "409", description = "Idempotency key is still being processed", content = []),
        ApiResponse(responseCode = "422", description = "Source and destination accounts are the same", content = []),
    )
    fun createTransfer(
        @PathVariable accountId: UUID,
        @Parameter(
            description = "Client-generated UUID that makes the submission safe to retry; the same " +
                "key returns the original transfer instead of creating a duplicate.",
            required = true,
        )
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreateTransferRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<TransferResponse> {
        val response = transferService.createTransfer(
            sourceAccountId = accountId,
            request = request,
            idempotencyKey = IdempotencyKey.fromUuid(idempotencyKey),
            requestContext = requestContextOf(httpRequest),
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    @GetMapping("/transfers/{id}")
    @Operation(summary = "Read a transfer's current status")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Transfer found"),
        ApiResponse(responseCode = "404", description = "No transfer with this id", content = []),
    )
    fun getTransfer(@PathVariable id: UUID): TransferResponse =
        transferService.findTransfer(id)

    /**
     * Captures the request-time fraud signals from the wire. Device and geo here
     * are client-asserted headers, so they are spoofable and NOT a real security
     * control — a fraudster controls exactly these values. Accepted knowingly for
     * this project's scope; see docs/adr/0008 for the threat and the real fix
     * (geo derived server-side from the IP, device attestation). All are optional
     * — the processor's rules abstain on whatever is missing.
     */
    private fun requestContextOf(request: HttpServletRequest) = RequestContext(
        deviceId = request.getHeader(DEVICE_ID_HEADER),
        ipAddress = clientIp(request),
        country = request.getHeader(GEO_COUNTRY_HEADER),
        latitude = request.getHeader(GEO_LATITUDE_HEADER)?.toDoubleOrNull(),
        longitude = request.getHeader(GEO_LONGITUDE_HEADER)?.toDoubleOrNull(),
    )

    /**
     * Prefers the first hop in `X-Forwarded-For` (the real client behind a proxy
     * or load balancer) and falls back to the socket's remote address.
     */
    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr

    private companion object {
        const val DEVICE_ID_HEADER = "X-Device-Id"
        const val GEO_COUNTRY_HEADER = "X-Geo-Country"
        const val GEO_LATITUDE_HEADER = "X-Geo-Latitude"
        const val GEO_LONGITUDE_HEADER = "X-Geo-Longitude"
    }
}
