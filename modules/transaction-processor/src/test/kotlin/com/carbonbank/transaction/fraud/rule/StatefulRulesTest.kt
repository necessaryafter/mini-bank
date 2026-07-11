package com.carbonbank.transaction.fraud.rule

import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.types.Money
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import com.carbonbank.transaction.fraud.store.model.AccountProfile
import com.carbonbank.transaction.fraud.store.RecipientBlocklist
import com.carbonbank.transaction.fraud.store.VelocityStore
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for the rule decision logic, with in-memory fakes for the stores.
 * The stores' real adapters are covered separately by FraudStoreIntegrationTest
 * against Testcontainers — see docs/adr/0009 for why the line is drawn here.
 */
class StatefulRulesTest {

    private val props = FraudProperties()
    private val source = UUID.randomUUID()
    private val destination = UUID.randomUUID()

    private class FakeProfileStore : AccountProfileStore {
        val data = mutableMapOf<UUID, AccountProfile>()
        override fun find(accountId: UUID) = data[accountId]
        override fun save(profile: AccountProfile) = profile.also { data[it.accountId] = it }
    }

    private class FakeBlocklist : RecipientBlocklist {
        val reported = mutableSetOf<UUID>()
        override fun isReported(accountId: UUID) = accountId in reported
        override fun report(accountId: UUID, reason: String?) { reported += accountId }
    }

    private class FakeVelocityStore(private val count: Long) : VelocityStore {
        override fun recordAndCount(accountId: UUID, window: Duration) = count
    }

    private fun transfer(
        amount: BigDecimal = BigDecimal("100.00"),
        requestedAt: Instant = Instant.parse("2026-07-09T12:00:00Z"),
        context: RequestContext? = null,
    ) = TransferUnderReview(
        transferId = UUID.randomUUID(),
        sourceAccountId = source,
        destinationAccountId = destination,
        amount = Money.from(amount),
        requestedAt = requestedAt,
        context = context,
    )

    @Test
    fun `velocity stays quiet under the limit`() {
        val rule = VelocityRule(FakeVelocityStore(props.velocity.maxTransfers.toLong()), props)
        assertNull(rule.evaluate(transfer()))
    }

    @Test
    fun `velocity fires above the limit`() {
        val rule = VelocityRule(FakeVelocityStore(props.velocity.maxTransfers + 1L), props)
        assertEquals("velocity", rule.evaluate(transfer())?.ruleId)
    }

    @Test
    fun `reported-recipient fires only for a blocklisted destination`() {
        val blocklist = FakeBlocklist()
        val rule = ReportedRecipientRule(blocklist, props)
        assertNull(rule.evaluate(transfer()))

        blocklist.report(destination, "mule")
        assertEquals("reported-recipient", rule.evaluate(transfer())?.ruleId)
    }

    @Test
    fun `amount-profile abstains on a cold profile`() {
        val profiles = FakeProfileStore()
        profiles.save(AccountProfile(source, transferCount = 1, amountSumCents = 5_000))
        val rule = AmountProfileRule(profiles, props)
        assertNull(rule.evaluate(transfer(BigDecimal("100000.00"))))
    }

    @Test
    fun `amount-profile fires far above the learned average`() {
        val profiles = FakeProfileStore()
        // 10 transfers averaging 50.00 -> threshold 10x = 500.00.
        profiles.save(AccountProfile(source, transferCount = 10, amountSumCents = 50_000))
        val rule = AmountProfileRule(profiles, props)

        assertNull(rule.evaluate(transfer(BigDecimal("300.00"))))
        assertEquals("amount-profile", rule.evaluate(transfer(BigDecimal("600.00")))?.ruleId)
    }

    @Test
    fun `unusual-hour fires outside the account's usual hours`() {
        val profiles = FakeProfileStore()
        profiles.save(AccountProfile(source, transferCount = 20, activeHours = setOf(7, 8, 9, 18, 19, 20)))
        val rule = UnusualHourRule(profiles, props)

        assertNull(rule.evaluate(transfer(requestedAt = Instant.parse("2026-07-09T08:30:00Z"))))
        assertEquals("unusual-hour", rule.evaluate(transfer(requestedAt = Instant.parse("2026-07-09T03:00:00Z")))?.ruleId)
    }

    // --- UnknownDeviceRule / UnexpectedCountryRule ---

    @Test
    fun `unknown-device fires for a device not in the profile`() {
        val profiles = FakeProfileStore()
        profiles.save(AccountProfile(source, transferCount = 5, knownDevices = setOf("android-1")))
        val rule = UnknownDeviceRule(profiles, props)

        assertNull(rule.evaluate(transfer(context = ctx(deviceId = "android-1"))))
        assertEquals("unknown-device", rule.evaluate(transfer(context = ctx(deviceId = "iphone-9")))?.ruleId)
    }

    @Test
    fun `unknown-device abstains without a device or a baseline`() {
        val profiles = FakeProfileStore()
        val rule = UnknownDeviceRule(profiles, props)
        assertNull(rule.evaluate(transfer(context = ctx(deviceId = null))))

        profiles.save(AccountProfile(source, transferCount = 1, knownDevices = emptySet()))
        assertNull(rule.evaluate(transfer(context = ctx(deviceId = "iphone-9"))))
    }

    @Test
    fun `unexpected-country fires for a country not in the profile`() {
        val profiles = FakeProfileStore()
        profiles.save(AccountProfile(source, transferCount = 5, knownCountries = setOf("BR")))
        val rule = UnexpectedCountryRule(profiles, props)

        assertNull(rule.evaluate(transfer(context = ctx(country = "BR"))))
        assertEquals("unexpected-country", rule.evaluate(transfer(context = ctx(country = "RO")))?.ruleId)
    }

    // --- ImpossibleTravelRule ---

    @Test
    fun `impossible-travel fires when the implied speed is superhuman`() {
        val profiles = FakeProfileStore()
        // Last seen in São Paulo 20 minutes ago...
        profiles.save(
            AccountProfile(
                source,
                transferCount = 5,
                lastLatitude = -23.55,
                lastLongitude = -46.63,
                lastSeenAt = Instant.parse("2026-07-09T11:40:00Z"),
            ),
        )
        val rule = ImpossibleTravelRule(profiles, props)

        // ...now transferring from Bucharest -> ~10,000km in 20min.
        val fromRomania = transfer(context = ctx(latitude = 44.43, longitude = 26.10))
        assertNotNull(rule.evaluate(fromRomania))

        // Same coordinates as last seen -> zero distance, no flag.
        val fromSaoPaulo = transfer(context = ctx(latitude = -23.55, longitude = -46.63))
        assertNull(rule.evaluate(fromSaoPaulo))
    }

    private fun ctx(
        deviceId: String? = null,
        country: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = RequestContext(deviceId, ipAddress = null, country = country, latitude = latitude, longitude = longitude)
}
