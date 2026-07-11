package com.carbonbank.transaction.config

import org.bson.UuidRepresentation
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * Forces STANDARD UUID encoding on the Mongo client. The `spring.data.mongodb.
 * uuid-representation` property alone is not enough here: the @ServiceConnection
 * customizer applies its connection string (with UNSPECIFIED representation)
 * and can win by ordering, leaving UUID @Id documents unencodable. Running this
 * customizer last pins the representation regardless of that ordering.
 */
@Configuration
class MongoUuidConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun uuidRepresentationCustomizer() = MongoClientSettingsBuilderCustomizer { builder ->
        builder.uuidRepresentation(UuidRepresentation.STANDARD)
    }
}
