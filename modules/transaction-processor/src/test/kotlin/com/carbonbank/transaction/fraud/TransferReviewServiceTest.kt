package com.carbonbank.transaction.fraud

import com.carbonbank.common.aws.sqs.impl.TransactionCreatedEvent
import com.carbonbank.common.aws.sqs.impl.TransactionDecisionEvent
import com.carbonbank.common.transaction.RequestContext
import com.carbonbank.common.transaction.TransferDecision
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import com.carbonbank.transaction.decision.DecisionPublisher
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.profile.AccountProfileUpdater
import com.carbonbank.transaction.fraud.rule.MaxAmountRule
import com.carbonbank.transaction.fraud.service.TransferReviewService
import com.carbonbank.transaction.fraud.store.AccountProfileStore
import com.carbonbank.transaction.fraud.store.model.AccountProfile
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TransferReviewServiceTest {

    private class FakeProfileStore : AccountProfileStore {
        val data = mutableMapOf<UUID, AccountProfile>()
        override fun find(accountId: UUID) = data[accountId]
        override fun save(profile: AccountProfile) = profile.also { data[it.accountId] = it }
    }

    private class FakeDecisionPublisher : DecisionPublisher {
        val published = mutableListOf<TransactionDecisionEvent>()
        override fun publish(decision: TransactionDecisionEvent) { published += decision }
    }

    private val source = UUID.randomUUID()
    private val idempotencyKey = IdempotencyKey.random()

    private fun event() = TransactionCreatedEvent(
        transferId = UUID.randomUUID(),
        sourceAccountId = source,
        destinationAccountId = UUID.randomUUID(),
        amount = Money.from(BigDecimal("100.00")),
        requestContext = RequestContext("android-1", "203.0.113.7", "BR", null, null),
        idempotencyKey = idempotencyKey,
        timestamp = Instant.parse("2026-07-09T12:00:00Z"),
    )

    /** Reviews one 100.00 transfer through an engine of exactly [rules]. */
    private fun review(vararg rules: FraudRule): Pair<TransactionDecisionEvent, FakeProfileStore> {
        val store = FakeProfileStore()
        val publisher = FakeDecisionPublisher()
        val service = TransferReviewService(FraudEngine(rules.toList()), AccountProfileUpdater(store), publisher)

        val transferEvent = event()
        service.review(transferEvent)

        val decision = publisher.published.single()
        assertEquals(transferEvent.transferId, decision.transferId)
        assertEquals(idempotencyKey, decision.idempotencyKey)
        return decision to store
    }

    private fun ruleScoring(points: Int) = FraudRule { RiskSignal("synthetic", points, "flagged") }

    @Test
    fun `a low-risk transfer is approved and folded into the profile`() {
        val (decision, store) = review(MaxAmountRule(FraudProperties()))

        assertEquals(TransferDecision.APPROVED, decision.decision)
        assertEquals(0, decision.score)
        assertEquals(1, store.find(source)!!.transferCount)
    }

    @Test
    fun `a high-risk transfer is held for review`() {
        val (decision, _) = review(ruleScoring(60))

        assertEquals(TransferDecision.UNDER_REVIEW, decision.decision)
        assertEquals(60, decision.score)
    }

    @Test
    fun `a critical transfer is rejected and carries its reasons`() {
        val (decision, _) = review(ruleScoring(100))

        assertEquals(TransferDecision.REJECTED, decision.decision)
        assertEquals(listOf("flagged"), decision.reasons)
    }
}
