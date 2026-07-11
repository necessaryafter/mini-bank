package com.carbonbank.common.transaction

/**
 * Request-time metadata captured by account-service when a transfer is
 * submitted and carried on the transfer event so the transaction-processor's
 * fraud rules can compare it against the user's historical behaviour (device,
 * network, geo).
 *
 * Every field is nullable: clients may omit the headers, and geo in particular
 * is a best-effort signal (a stubbed provider locally; an IP-geolocation lookup
 * in production). Rules that need a field must abstain when it is absent rather
 * than assume innocence or guilt.
 */
data class RequestContext(
    val deviceId: String?,
    val ipAddress: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
)
