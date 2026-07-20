package com.carbonbank.account.statement

import io.awspring.cloud.s3.ObjectMetadata
import io.awspring.cloud.s3.S3Template
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Stores generated statement PDFs in S3 (floci locally) and hands back a
 * pre-signed URL so a caller can download the file directly from S3 without any
 * credentials — the account-service never proxies the bytes.
 */
@Component
class S3StatementStore(
    private val s3Template: S3Template,
    @param:Value($$"${carbonbank.statements.bucket}") private val bucket: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates the bucket on startup so `docker compose up` is self-sufficient —
     * floci boots empty and there is no provisioning script. createBucket in
     * us-east-1 is idempotent (a re-create by the owner returns 200), so this is
     * safe to run every boot. Failures are swallowed and only warned: a context
     * that never exports a statement (e.g. most tests) must not fail to start
     * just because S3 is unreachable; the first export would surface the error.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun ensureBucket() {
        runCatching { s3Template.createBucket(bucket) }
            .onFailure { log.warn("Could not ensure statements bucket '{}' exists: {}", bucket, it.message) }
    }

    fun upload(accountId: UUID, pdf: ByteArray): String {
        val key = "statements/$accountId/${TIMESTAMP.format(Instant.now())}.pdf"
        s3Template.upload(
            bucket,
            key,
            pdf.inputStream(),
            ObjectMetadata.builder().contentType("application/pdf").build(),
        )
        return key
    }

    fun presignedUrl(key: String): String =
        s3Template.createSignedGetURL(bucket, key, URL_TTL).toString()

    companion object {
        val URL_TTL: Duration = Duration.ofMinutes(15)

        // Colons are illegal-ish in S3 keys and ugly in URLs; use a filesystem-safe stamp.
        private val TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC)
    }
}
