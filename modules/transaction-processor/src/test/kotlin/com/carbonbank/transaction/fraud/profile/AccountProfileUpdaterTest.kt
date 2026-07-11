package com.carbonbank.transaction.fraud.profile

import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.types.Money
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import com.carbonbank.transaction.fraud.store.model.AccountProfile
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountProfileUpdaterTest {

    private class FakeProfileStore : AccountProfileStore {
        val data = mutableMapOf<UUID, AccountProfile>()
        override fun find(accountId: UUID) = data[accountId]
        override fun save(profile: AccountProfile) = profile.also { data[it.accountId] = it }
    }

    private val store = FakeProfileStore()
    private val updater = AccountProfileUpdater(store)
    private val account = UUID.randomUUID()

    private fun transfer(
        amount: BigDecimal = BigDecimal("50.00"),
        requestedAt: Instant = Instant.parse("2026-07-09T08:00:00Z"),
        context: RequestContext? = null,
    ) = TransferUnderReview(
        transferId = UUID.randomUUID(),
        sourceAccountId = account,
        destinationAccountId = UUID.randomUUID(),
        amount = Money.from(amount),
        requestedAt = requestedAt,
        context = context,
    )

    @Test
    fun `first transfer creates the profile from cold`() {
        updater.record(
            transfer(
                amount = BigDecimal("40.00"),
                context = RequestContext("android-1", null, "BR", -23.55, -46.63),
            ),
        )

        val profile = store.find(account)!!
        assertEquals(1, profile.transferCount)
        assertEquals(4_000, profile.amountSumCents)
        assertEquals(setOf("android-1"), profile.knownDevices)
        assertEquals(setOf("BR"), profile.knownCountries)
        assertEquals(setOf(8), profile.activeHours)
        assertEquals(-23.55, profile.lastLatitude)
    }

    @Test
    fun `subsequent transfers accumulate into the profile`() {
        updater.record(transfer(amount = BigDecimal("40.00"), requestedAt = Instant.parse("2026-07-09T08:00:00Z")))
        updater.record(transfer(amount = BigDecimal("60.00"), requestedAt = Instant.parse("2026-07-09T14:00:00Z")))

        val profile = store.find(account)!!
        assertEquals(2, profile.transferCount)
        assertEquals(10_000, profile.amountSumCents)
        assertEquals(5_000, profile.averageAmountCents)
        assertEquals(setOf(8, 14), profile.activeHours)
    }

    @Test
    fun `a geo-less transfer leaves the last-known position untouched`() {
        updater.record(transfer(context = RequestContext(null, null, "BR", -23.55, -46.63)))
        updater.record(transfer(context = RequestContext("android-1", null, null, null, null)))

        val profile = store.find(account)!!
        assertEquals(-23.55, profile.lastLatitude)
        assertEquals(-46.63, profile.lastLongitude)
    }

    @Test
    fun `no profile exists before the first transfer`() {
        assertNull(store.find(account))
    }
}
