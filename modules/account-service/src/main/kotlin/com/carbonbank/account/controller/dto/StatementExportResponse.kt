package com.carbonbank.account.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Reference to a generated statement file stored in S3.")
data class StatementExportResponse(
    @field:Schema(description = "File format of the export.", example = "pdf")
    val format: String,

    @field:Schema(description = "S3 object key where the file was stored.", example = "statements/3f2a.../2026-07-19T10-31-00Z.pdf")
    val key: String,

    @field:Schema(
        description = "Pre-signed, time-limited URL to download the file directly from S3 — no credentials needed.",
    )
    val url: String,

    @field:Schema(description = "How long the pre-signed URL stays valid, in seconds.", example = "900")
    val expiresInSeconds: Long,
)
