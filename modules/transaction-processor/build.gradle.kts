plugins {
    alias(libs.plugins.kotlin.plugin.spring)
    alias(libs.plugins.spring.boot)
}

description = "Consumes account-service events from AWS SQS and processes transactions asynchronously"

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

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.cloud.aws.starter.sqs)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    developmentOnly(libs.spring.boot.devtools)
    developmentOnly(libs.spring.boot.docker.compose)
    runtimeOnly(libs.micrometer.registry.prometheus)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(testFixtures(project(":modules:common")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.redis.test)
    testImplementation(libs.spring.boot.starter.data.mongodb.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.floci)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
