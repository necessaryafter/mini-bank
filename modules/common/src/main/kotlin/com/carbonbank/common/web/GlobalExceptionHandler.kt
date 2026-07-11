package com.carbonbank.common.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * Base RFC 7807 (application/problem+json) exception handling shared by every service.
 * Import [com.carbonbank.common.web.ApiException] for domain-level errors so they map
 * to the correct HTTP status without each service re-implementing this wiring.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ProblemDetail =
        problemDetail(ex.status, ex.message ?: ex.status.reasonPhrase)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val detail = ex.bindingResult.fieldErrors
            .joinToString(separator = "; ") { "${it.field}: ${it.defaultMessage}" }
        return problemDetail(HttpStatus.BAD_REQUEST, detail.ifBlank { "Validation failed" })
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ProblemDetail =
        problemDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error")
    }

    private fun problemDetail(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            setProperty("timestamp", Instant.now())
        }
}

/**
 * Carries the HTTP status alongside the exception so [GlobalExceptionHandler]
 * can map any domain error to the right response without each service writing
 * its own `@ExceptionHandler` for every exception type it defines.
 */
open class ApiException(
    val status: HttpStatus,
    message: String,
) : RuntimeException(message)
