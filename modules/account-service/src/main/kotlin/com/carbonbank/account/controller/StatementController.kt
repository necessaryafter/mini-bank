package com.carbonbank.account.controller

import com.carbonbank.account.controller.dto.StatementExportResponse
import com.carbonbank.account.controller.dto.StatementResponse
import com.carbonbank.account.service.StatementService
import com.carbonbank.account.statement.StatementExportService
import com.carbonbank.common.web.ApiException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Statements", description = "Read an account's movement history and export it as a PDF")
class StatementController(
    private val statementService: StatementService,
    private val exportService: StatementExportService,
) {

    @GetMapping("/accounts/{id}/statement")
    @Operation(
        summary = "Read an account statement",
        description = "Returns the account's movements newest-first, paginated by keyset on the " +
            "'sequence' cursor. Follow 'nextCursor' to page further back; a null cursor means the start.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Statement page returned"),
        ApiResponse(responseCode = "404", description = "No account with this id", content = []),
    )
    fun getStatement(
        @PathVariable id: UUID,
        @Parameter(description = "Return movements older than this sequence; omit for the most recent page.")
        @RequestParam(required = false) cursor: Long?,
        @Parameter(description = "Max movements per page (1–${StatementService.MAX_PAGE_SIZE}).")
        @RequestParam(defaultValue = "50") limit: Int,
    ): StatementResponse =
        statementService.getStatement(id, cursor, limit)

    @GetMapping("/accounts/{id}/statement/export")
    @Operation(
        summary = "Export an account statement to S3",
        description = "Renders the full history as a PDF, stores it in S3, and returns a short-lived " +
            "pre-signed URL to download it directly from S3.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "File generated; response carries the pre-signed URL"),
        ApiResponse(responseCode = "400", description = "Unsupported format", content = []),
        ApiResponse(responseCode = "404", description = "No account with this id", content = []),
    )
    fun exportStatement(
        @PathVariable id: UUID,
        @Parameter(description = "Export format. Only 'pdf' is supported.")
        @RequestParam(defaultValue = "pdf") format: String,
    ): StatementExportResponse {
        if (!format.equals("pdf", ignoreCase = true)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Unsupported statement format '$format'; only 'pdf' is supported")
        }
        return exportService.exportPdf(id)
    }
}
