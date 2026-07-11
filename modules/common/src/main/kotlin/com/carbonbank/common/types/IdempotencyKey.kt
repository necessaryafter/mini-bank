package com.carbonbank.common.types

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID

@JvmInline
value class IdempotencyKey private constructor(@get:JsonValue val value: String) {

    companion object {
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun random(): IdempotencyKey = IdempotencyKey(UUID.randomUUID().toString())

        @JsonCreator
        @JvmStatic
        fun fromUuid(value: String): IdempotencyKey {
            require(UUID_REGEX.matches(value)) { "Invalid UUID format: $value" }
            return IdempotencyKey(value)
        }
    }

    override fun toString(): String = value
}