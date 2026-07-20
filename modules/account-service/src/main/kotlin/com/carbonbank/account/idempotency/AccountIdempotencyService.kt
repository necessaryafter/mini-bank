package com.carbonbank.account.idempotency

import com.carbonbank.common.types.IdempotencyKey
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * Claims an [IdempotencyKey] to a single account id via Redis `SETNX`, so
 * concurrent `POST /accounts` requests carrying the same client key race for
 * one winner instead of each creating their own account. The unique
 * `idempotency_key` index on `accounts` (see V3) is the durable backstop if the
 * cache is unavailable or a key expires mid-flight.
 *
 * Structurally identical to
 * [com.carbonbank.account.idempotency.TransferIdempotencyService]; kept separate
 * (own key namespace) so each aggregate's idempotency concern stays explicit.
 */
@Service
class AccountIdempotencyService(
    private val redis: StringRedisTemplate,
) {
    private val ttl = Duration.ofHours(24)

    fun reserve(idempotencyKey: IdempotencyKey, accountId: UUID): Boolean =
        redis.opsForValue().setIfAbsent(key(idempotencyKey), accountId.toString(), ttl) == true

    fun accountIdFor(idempotencyKey: IdempotencyKey): UUID? =
        redis.opsForValue().get(key(idempotencyKey))?.let(UUID::fromString)

    fun release(idempotencyKey: IdempotencyKey) {
        redis.delete(key(idempotencyKey))
    }

    private fun key(idempotencyKey: IdempotencyKey) = "idempotency:account:$idempotencyKey"
}
