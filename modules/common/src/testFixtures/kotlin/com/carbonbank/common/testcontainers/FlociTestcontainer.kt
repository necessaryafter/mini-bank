package com.carbonbank.common.testcontainers

import io.floci.testcontainers.FlociContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean

/**
 * Runs floci (see compose.yaml), the AWS emulator this project uses for local
 * dev, instead of the official localstack/localstack image. `FlociContainer`
 * comes from `io.floci:testcontainers-floci`; the `@ServiceConnection` here
 * works because `io.floci:spring-boot-testcontainers-floci` ships its own
 * `FlociAwsContainerConnectionDetailsFactory`, so Spring Cloud AWS clients get
 * their endpoint, region, and credentials wired automatically, the same way
 * they would for the official LocalStack module.
 *
 * Starts with every emulated service disabled, then re-enables only SQS and
 * S3: the two this project actually talks to (queues + statement PDFs).
 */
@TestConfiguration(proxyBeanMethods = false)
class FlociTestcontainer {

    @Bean
    @ServiceConnection
    fun flociContainer(): FlociContainer =
        FlociContainer()
            .disableAllServices()
            .withSqsConfig { it.enabled(true) }
            .withS3Config { it.enabled(true) }
}
