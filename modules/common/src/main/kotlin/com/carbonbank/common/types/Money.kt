package com.carbonbank.common.types

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.math.BigDecimal

@JvmInline
value class Money private constructor(@get:JsonValue val cents: Long) : Comparable<Money> {

    companion object {
        val ZERO = Money(0)

        fun from(decimal: BigDecimal): Money = Money(decimal
            .movePointRight(2)
            .longValueExact())

        @JsonCreator
        @JvmStatic
        fun fromCents(cents: Long): Money = Money(cents)
    }

    operator fun plus(other: Money) = Money(cents + other.cents)

    operator fun minus(other: Money) = Money(cents - other.cents)

    override fun compareTo(other: Money) = cents.compareTo(other.cents)

    fun toBigDecimal(): BigDecimal =
        BigDecimal.valueOf(cents, 2)
}