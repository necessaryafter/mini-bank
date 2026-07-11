package com.carbonbank.account.idempotency

import com.carbonbank.common.types.IdempotencyKey
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * Claims an [IdempotencyKey] to a single transfer id via Redis `SETNX`, so
 * concurrent requests carrying the same client-supplied key race for one
 * winner instead of each creating their own PENDING transaction. The
 * `idempotency_key` unique index on `transactions` (see V1__init.sql) is the
 * durable backstop if this cache is ever unavailable or a key expires mid-flight.
 */
@Service
class TransferIdempotencyService(
    private val redis: StringRedisTemplate,
) {
    private val ttl = Duration.ofHours(24)

    fun reserve(idempotencyKey: IdempotencyKey, transactionId: UUID): Boolean =
        redis.opsForValue().setIfAbsent(key(idempotencyKey), transactionId.toString(), ttl) == true

    fun transactionIdFor(idempotencyKey: IdempotencyKey): UUID? =
        redis.opsForValue().get(key(idempotencyKey))?.let(UUID::fromString)

    fun release(idempotencyKey: IdempotencyKey) {
        redis.delete(key(idempotencyKey))
    }

    private fun key(idempotencyKey: IdempotencyKey) = "idempotency:transfer:$idempotencyKey"
}
