package com.carbonbank.account.statement

import io.floci.testcontainers.FlociContainer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Proves the S3 strategy the statement export relies on actually works against a
 * floci endpoint: path-style access plus a pre-signed GET URL that a plain HTTP
 * client (no AWS credentials) can download. Deliberately not a
 * @SpringBootTest, it wires the SDK directly so it neither starts the account
 * context nor touches the shared Exposed database (see AccountTestConfig).
 */
@Testcontainers
class S3PresignRoundTripIT {

    @Test
    fun `uploads a pdf and downloads it back through a presigned url`() {
        val endpoint = URI.create(floci.getEndpoint())
        val credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(floci.accessKey, floci.secretKey),
        )
        val region = Region.of(floci.region)

        val s3 = S3Client.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(region)
            .forcePathStyle(true)
            .build()

        val bucket = "statements"
        val key = "statements/round-trip/test.pdf"
        val body = "%PDF-1.4 round-trip".toByteArray()

        s3.createBucket { it.bucket(bucket) }
        s3.putObject(
            { it.bucket(bucket).key(key).contentType("application/pdf") },
            RequestBody.fromBytes(body),
        )

        val presigner = S3Presigner.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(region)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

        val presigned = presigner.presignGetObject { presign ->
            presign.signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest { it.bucket(bucket).key(key) }
        }

        // Force HTTP/1.1: the default client negotiates HTTP/2 (h2c upgrade over
        // cleartext), which the floci gateway drops mid-handshake and shows up
        // as a ClosedChannelException rather than a real download failure.
        val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
        val response = client.send(
            HttpRequest.newBuilder(presigned.url().toURI()).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

        assertEquals(200, response.statusCode())
        assertArrayEquals(body, response.body())
    }

    companion object {
        // floci, this project's AWS emulator (see compose.yaml and
        // FlociTestcontainer), not the official localstack/localstack image.
        // Starts with every service disabled, then re-enables only S3.
        @Container
        @JvmStatic
        val floci: FlociContainer = FlociContainer().disableAllServices().withS3Config { it.enabled(true) }
    }
}
