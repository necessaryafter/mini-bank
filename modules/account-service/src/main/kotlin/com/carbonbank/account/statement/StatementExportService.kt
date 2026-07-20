package com.carbonbank.account.statement

import com.carbonbank.account.controller.dto.StatementExportResponse
import com.carbonbank.account.service.StatementService
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Orchestrates a statement export: project the full history, render it, store it
 * in S3, and return a pre-signed download URL. The account-existence 404 comes
 * from [StatementService.fullStatement], so an export of an unknown account fails
 * the same way a read does.
 */
@Service
class StatementExportService(
    private val statementService: StatementService,
    private val pdfRenderer: StatementPdfRenderer,
    private val store: S3StatementStore,
) {

    fun exportPdf(accountId: UUID): StatementExportResponse {
        val statement = statementService.fullStatement(accountId)
        val pdf = pdfRenderer.render(statement)
        val key = store.upload(accountId, pdf)
        return StatementExportResponse(
            format = "pdf",
            key = key,
            url = store.presignedUrl(key),
            expiresInSeconds = S3StatementStore.URL_TTL.seconds,
        )
    }
}
