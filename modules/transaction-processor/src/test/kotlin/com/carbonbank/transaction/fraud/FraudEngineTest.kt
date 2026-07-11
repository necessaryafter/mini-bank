package com.carbonbank.transaction.fraud

import com.carbonbank.common.types.Money
import com.carbonbank.transaction.fraud.config.FraudProperties
import com.carbonbank.transaction.fraud.model.FraudAction
import com.carbonbank.transaction.fraud.model.RiskBand
import com.carbonbank.transaction.fraud.model.RiskSignal
import com.carbonbank.transaction.fraud.model.TransferUnderReview
import com.carbonbank.transaction.fraud.rule.MaxAmountRule
import com.carbonbank.transaction.fraud.rule.NonPositiveAmountRule
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FraudEngineTest {

    private val properties = FraudProperties(maxAmount = FraudProperties.MaxAmount(threshold = BigDecimal("10000.00")))
    private val engine = FraudEngine(listOf(MaxAmountRule(properties), NonPositiveAmountRule(properties)))

    private fun transfer(amount: BigDecimal) = TransferUnderReview(
        transferId = UUID.randomUUID(),
        sourceAccountId = UUID.randomUUID(),
        destinationAccountId = UUID.randomUUID(),
        amount = Money.from(amount),
        requestedAt = Instant.now(),
        context = null,
    )

    @Test
    fun `scores zero and lands LOW for a normal transfer`() {
        val evaluation = engine.evaluate(transfer(BigDecimal("100.00")))

        assertEquals(0, evaluation.score)
        assertEquals(RiskBand.LOW, evaluation.band)
        assertEquals(listOf(FraudAction.APPROVE), evaluation.actions)
    }

    @Test
    fun `a transfer exactly at the limit is not flagged`() {
        assertEquals(RiskBand.LOW, engine.evaluate(transfer(BigDecimal("10000.00"))).band)
    }

    @Test
    fun `an above-limit transfer contributes the max-amount score`() {
        val evaluation = engine.evaluate(transfer(BigDecimal("10000.01")))

        assertEquals(properties.maxAmount.score, evaluation.score)
        assertEquals(RiskBand.MEDIUM, evaluation.band)
        assertEquals("max-amount", evaluation.signals.single().ruleId)
    }

    @Test
    fun `a non-positive amount lands CRITICAL and blocks`() {
        val evaluation = engine.evaluate(transfer(BigDecimal.ZERO))

        assertEquals(RiskBand.CRITICAL, evaluation.band)
        assertTrue(FraudAction.BLOCK in evaluation.actions)
    }

    @Test
    fun `scores from multiple rules accumulate and escalate the band`() {
        // Two independent rules firing at 30 each sum to 60 -> HIGH.
        val thirtyPointRule = { id: String -> FraudRule { RiskSignal(id, 30, "flagged by $id") } }
        val engine = FraudEngine(listOf(thirtyPointRule("a"), thirtyPointRule("b")))

        val evaluation = engine.evaluate(transfer(BigDecimal("100.00")))

        assertEquals(2, evaluation.signals.size)
        assertEquals(60, evaluation.score)
        assertEquals(RiskBand.HIGH, evaluation.band)
    }

    @Test
    fun `no registered rules approves everything`() {
        val emptyEngine = FraudEngine(emptyList())
        val evaluation = emptyEngine.evaluate(transfer(BigDecimal("999999.00")))

        assertEquals(0, evaluation.score)
        assertEquals(RiskBand.LOW, evaluation.band)
    }
}
