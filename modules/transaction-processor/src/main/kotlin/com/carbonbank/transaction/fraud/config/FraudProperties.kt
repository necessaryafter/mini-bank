package com.carbonbank.transaction.fraud.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

/**
 * Tunable thresholds and per-rule score weights, bound from the `fraud.*`
 * prefix so anti-fraud tuning is a config change, not a code change. Each rule
 * owns a nested block with its own `score` (points added when it fires) plus
 * whatever thresholds it needs.
 */
@ConfigurationProperties(prefix = "fraud")
data class FraudProperties(
    val maxAmount: MaxAmount = MaxAmount(),
    val nonPositiveAmount: Score = Score(score = 100),
    val velocity: Velocity = Velocity(),
    val reportedRecipient: Score = Score(score = 70),
    val amountProfile: AmountProfile = AmountProfile(),
    val unusualHour: UnusualHour = UnusualHour(),
    val unknownDevice: Score = Score(score = 30),
    val unexpectedCountry: Score = Score(score = 35),
    val impossibleTravel: ImpossibleTravel = ImpossibleTravel(),
) {
    data class Score(val score: Int)

    data class MaxAmount(
        val threshold: BigDecimal = BigDecimal("10000.00"),
        val score: Int = 40,
    )

    data class Velocity(
        val windowSeconds: Long = 60,
        val maxTransfers: Int = 5,
        val score: Int = 30,
    )

    data class AmountProfile(
        // Guards the cold start: below this much history the learned average
        // isn't trustworthy, so the rule abstains instead of firing on noise.
        val minSamples: Long = 5,
        // Flagged when the amount exceeds multiplier * the account's average.
        val multiplier: Double = 10.0,
        val score: Int = 40,
    )

    data class UnusualHour(
        // Cold-start guard, as in AmountProfile.
        val minSamples: Long = 10,
        val score: Int = 20,
    )

    data class ImpossibleTravel(
        val maxSpeedKmh: Double = 900.0,
        val score: Int = 60,
    )
}