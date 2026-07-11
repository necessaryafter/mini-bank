package com.carbonbank.transaction.fraud.store

import com.carbonbank.common.testcontainers.MongoTestcontainer
import com.carbonbank.common.testcontainers.RedisTestcontainer
import com.carbonbank.transaction.fraud.store.model.AccountProfile
import com.carbonbank.transaction.support.SqsTestConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(properties = ["spring.cloud.aws.sqs.enabled=false"])
@Import(RedisTestcontainer::class, MongoTestcontainer::class, SqsTestConfig::class)
class FraudStoreIntegrationTest {

    @Autowired lateinit var profiles: AccountProfileStore
    @Autowired lateinit var blocklist: RecipientBlocklist
    @Autowired lateinit var velocity: VelocityStore

    @Test
    fun `profile round-trips through mongo`() {
        val id = UUID.randomUUID()
        profiles.save(
            AccountProfile(
                accountId = id,
                transferCount = 4,
                amountSumCents = 20_000,
                knownDevices = setOf("device-a"),
                knownCountries = setOf("BR"),
                activeHours = setOf(9, 10, 11),
            ),
        )

        val loaded = profiles.find(id)!!
        assertEquals(4, loaded.transferCount)
        assertEquals(5_000, loaded.averageAmountCents)
        assertEquals(setOf("device-a"), loaded.knownDevices)
    }

    @Test
    fun `blocklist reflects reported recipients`() {
        val id = UUID.randomUUID()
        assertFalse(blocklist.isReported(id))

        blocklist.report(id, "mule account")

        assertTrue(blocklist.isReported(id))
    }

    @Test
    fun `velocity counts repeated transfers within the window`() {
        val id = UUID.randomUUID()
        val window = Duration.ofMinutes(1)

        assertEquals(1, velocity.recordAndCount(id, window))
        assertEquals(2, velocity.recordAndCount(id, window))
        assertEquals(3, velocity.recordAndCount(id, window))
    }
}
