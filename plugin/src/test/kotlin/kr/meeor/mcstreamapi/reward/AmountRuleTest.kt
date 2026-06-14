package kr.meeor.mcstreamapi.reward

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmountRuleTest {
    @Test
    fun `parses exact amount`() {
        val rule = AmountRule.parse("1000").getOrThrow()

        assertIs<AmountRule.Exact>(rule)
        assertTrue(rule.matches(1000))
        assertFalse(rule.matches(999))
    }

    @Test
    fun `parses range amount`() {
        val rule = AmountRule.parse("1000-5000").getOrThrow()

        assertIs<AmountRule.Range>(rule)
        assertTrue(rule.matches(1000))
        assertTrue(rule.matches(5000))
        assertFalse(rule.matches(5001))
    }

    @Test
    fun `parses plus amount`() {
        val rule = AmountRule.parse("5000+").getOrThrow()

        assertIs<AmountRule.Plus>(rule)
        assertTrue(rule.matches(5000))
        assertTrue(rule.matches(9999))
        assertFalse(rule.matches(4999))
    }

    @Test
    fun `rejects invalid amount syntax`() {
        assertTrue(AmountRule.parse("abc").isFailure)
        assertTrue(AmountRule.parse("5000-1000").isFailure)
        assertTrue(AmountRule.parse("0").isFailure)
        assertTrue(AmountRule.parse("0+").isFailure)
    }
}
