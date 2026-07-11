package com.carbonbank.common.testcontainers

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

/**
 * Must use the deprecated `org.testcontainers.containers.localstack` package:
 * Spring Boot 4.1's LocalStack `@ServiceConnection` factory only matches that
 * type, not the newer `org.testcontainers.localstack` one from testcontainers 2.x.
 */
@TestConfiguration(proxyBeanMethods = false)
class LocalStackTestcontainer {

    @Bean
    @ServiceConnection
    fun localStackContainer(): LocalStackContainer =
        LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.S3)
}
