package com.carbonbank.common.persistence

import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * Persists [Money] as its underlying cents (BIGINT), since Exposed cannot
 * map a Kotlin value class to a column on its own.
 */
fun Table.money(name: String): Column<Money> =
    long(name).transform(wrap = { Money.fromCents(it) }, unwrap = { it.cents })

/**
 * Persists [IdempotencyKey] as its underlying UUID string, since Exposed cannot
 * map a Kotlin value class to a column on its own.
 */
fun Table.idempotencyKey(name: String): Column<IdempotencyKey> =
    varchar(name, 36).transform(wrap = { IdempotencyKey.fromUuid(it) }, unwrap = { it.value })

/**
 * Nullable variant of [idempotencyKey], for aggregates where the key is optional:
 * the genesis account and test-created accounts have none, while every account
 * created through the API carries one (see the unique index in V3).
 */
fun Table.idempotencyKeyNullable(name: String): Column<IdempotencyKey?> =
    varchar(name, 36).nullable().transform(wrap = { it?.let(IdempotencyKey::fromUuid) }, unwrap = { it?.value })

/**
 * Nullable variant of [money], for columns that only get a value once an
 * entry is posted (e.g. `balance_after`, which stays null while a hold is
 * still pending).
 */
fun Table.moneyNullable(name: String): Column<Money?> =
    long(name).nullable().transform(wrap = { it?.let(Money::fromCents) }, unwrap = { it?.cents })
