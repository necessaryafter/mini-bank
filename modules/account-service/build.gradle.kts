plugins {
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring.boot)
}

description = "Owns bank account data and the double-entry ledger of transactions"

// Generates META-INF/build-info.properties so /actuator/info reports the running
// build (name, version, timestamp) instead of an empty payload.
springBoot {
    buildInfo()
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.aws.dependencies.get().toString())
    }
}

dependencies {
    implementation(project(":modules:common"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.cloud.aws.starter.sqs)
    implementation(libs.spring.cloud.aws.starter.s3)
    implementation(libs.openpdf)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.spring.boot.starter)
    developmentOnly(libs.spring.boot.devtools)
    developmentOnly(libs.spring.boot.docker.compose)
    runtimeOnly(libs.micrometer.registry.prometheus)
    runtimeOnly(libs.postgresql)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(testFixtures(project(":modules:common")))
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.data.redis.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
