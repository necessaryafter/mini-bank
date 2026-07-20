package com.carbonbank.account.statement

import io.awspring.cloud.s3.ObjectMetadata
import io.awspring.cloud.s3.S3Template
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.InputStream
import java.net.URI
import java.time.Duration
import java.util.UUID

class S3StatementStoreTest {

    private val s3 = mock(S3Template::class.java)
    private val store = S3StatementStore(s3, BUCKET)

    @Test
    fun `upload stores the pdf under an account-scoped key`() {
        val accountId = UUID.randomUUID()

        val key = store.upload(accountId, byteArrayOf(1, 2, 3))

        assertTrue(key.startsWith("statements/$accountId/"), "key should be namespaced by account")
        assertTrue(key.endsWith(".pdf"), "key should carry the .pdf suffix")
        verify(s3).upload(eq(BUCKET), eq(key), any(InputStream::class.java), any(ObjectMetadata::class.java))
    }

    @Test
    fun `presignedUrl delegates to the template and returns the url string`() {
        val key = "statements/x/file.pdf"
        `when`(s3.createSignedGetURL(eq(BUCKET), eq(key), any(Duration::class.java)))
            .thenReturn(URI("http://localhost:4566/$BUCKET/$key?sig=abc").toURL())

        assertEquals("http://localhost:4566/$BUCKET/$key?sig=abc", store.presignedUrl(key))
    }

    companion object {
        private const val BUCKET = "statements"
    }
}
