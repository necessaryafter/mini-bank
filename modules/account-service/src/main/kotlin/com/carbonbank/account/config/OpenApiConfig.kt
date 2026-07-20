package com.carbonbank.account.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Serves the OpenAPI document (and Swagger UI) that springdoc builds from the
 * controllers. Only the top-level metadata is set here; per-endpoint docs live
 * on the controllers/DTOs as annotations, next to the code they describe.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun accountServiceOpenApi(
        @Value("\${spring.application.name}") applicationName: String,
    ): OpenAPI = OpenAPI().info(
        Info()
            .title("CarbonBank — Account Service API")
            .description(
                """
                REST surface of the account-service: opening accounts, reading balances,
                statements, and submitting money transfers.

                Transfers are accepted asynchronously (`202 Accepted`) and settle through the
                transaction-processor's fraud/decision pipeline — poll `GET /transfers/{id}`
                for the terminal status. Errors follow RFC 7807 (`application/problem+json`).
                """.trimIndent(),
            )
            .version("v1")
            .contact(Contact().name("CarbonBank").email("engineering@carbonbank.example"))
            .license(License().name("Proprietary")),
    )
}
