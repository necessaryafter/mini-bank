package com.carbonbank.transaction.fraud.store.impl

import com.carbonbank.transaction.fraud.store.VelocityStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Redis-backed sliding window: each transfer is a member of a sorted set scored
 * by its epoch-milli timestamp. Every call appends the current timestamp, trims
 * members older than the window, refreshes the key's TTL, and returns the set
 * size — so expiry of old activity is free (both per-member trim and whole-key
 * TTL). The key namespace (`fraud:`) is the processor's own (see docs/adr/0007).
 */
@Component
class RedisVelocityStore(
    private val redis: StringRedisTemplate,
) : VelocityStore {

    override fun recordAndCount(accountId: UUID, window: Duration): Long {
        val key = "$KEY_PREFIX$accountId"
        val now = Instant.now().toEpochMilli()
        val cutoff = (now - window.toMillis()).toDouble()

        val zset = redis.opsForZSet()

        // Member must be unique per transfer, so two transfers in the same
        // millisecond both count instead of collapsing to one score update.
        zset.add(key, "$now-${UUID.randomUUID()}", now.toDouble())
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff)
        redis.expire(key, window)
        return zset.zCard(key) ?: 0L
    }

    private companion object {
        const val KEY_PREFIX = "fraud:velocity:"
    }
}
