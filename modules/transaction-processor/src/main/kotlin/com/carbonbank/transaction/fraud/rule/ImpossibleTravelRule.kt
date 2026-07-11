package com.carbonbank.transaction.fraud.rule

import com.carbonbank.transaction.fraud.FraudRule
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Flags the "impossible travel" pattern: two transfers whose locations are too
 * far apart to have been reached in the elapsed time (a transfer from São Paulo
 * and, twenty minutes later, one from Bucharest). This catches a stolen session
 * or credential replay that the per-transfer country/device rules might miss.
 */
@Component
class ImpossibleTravelRule(
    private val profiles: AccountProfileStore,
    private val properties: FraudProperties,
) : FraudRule {

    override fun evaluate(transfer: TransferUnderReview): RiskSignal? {
        val lat = transfer.context?.latitude ?: return null
        val lon = transfer.context.longitude ?: return null

        val profile = profiles.find(transfer.sourceAccountId) ?: return null
        val lastLat = profile.lastLatitude ?: return null
        val lastLon = profile.lastLongitude ?: return null
        val lastSeen = profile.lastSeenAt ?: return null

        val hours = Duration.between(lastSeen, transfer.requestedAt).toMillis() / MILLIS_PER_HOUR

        // Non-positive elapsed time means clock skew or two events at the same
        // instant — there's no meaningful speed to compute, so abstain.
        if (hours <= 0) return null

        val dLatDeg = lat - lastLat
        val dLonDeg = lon - lastLon

        // Cheap upper bound on great-circle distance: the sum of the meridian
        // (latitude-only) and worst-case parallel (longitude-only, cos=1)
        // distances is always >= the real haversine distance. If even this
        // over-estimate implies a speed under the limit, the real distance
        // certainly does too - skip the trig entirely. This short-circuits
        // the vast majority of transfers, which come from nearby locations.
        val upperBoundKm = KM_PER_DEGREE * (abs(dLatDeg) + abs(dLonDeg))
        if (upperBoundKm / hours <= properties.impossibleTravel.maxSpeedKmh) return null

        val km = haversineKm(lastLat, lastLon, lat, lon, dLatDeg, dLonDeg)
        val speedKmh = km / hours
        if (speedKmh <= properties.impossibleTravel.maxSpeedKmh) return null

        return RiskSignal(
            ruleId = "impossible-travel",
            score = properties.impossibleTravel.score,
            reason = "Implied ${"%.0f".format(km)}km in ${"%.2f".format(hours)}h (${"%.0f".format(speedKmh)} km/h)",
        )
    }

    private fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        dLatDeg: Double,
        dLonDeg: Double,
    ): Double {
        val dLat = Math.toRadians(dLatDeg)
        val dLon = Math.toRadians(dLonDeg)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val a = sinLat * sinLat +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinLon * sinLon

        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0
        const val KM_PER_DEGREE = 111.32
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}