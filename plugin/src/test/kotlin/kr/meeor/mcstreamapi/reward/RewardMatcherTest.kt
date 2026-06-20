package kr.meeor.mcstreamapi.reward

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RewardMatcherTest {
    @Test
    fun `exact reward wins over range and plus`() {
        val rewards = listOf(
            reward("plus", AmountRule.Plus(1000)),
            reward("range", AmountRule.Range(1000, 2000)),
            reward("exact", AmountRule.Exact(1500)),
        )

        val matched = RewardMatcher(FixedRandomSource(0)).match(rewards, 1500)

        assertEquals("exact", matched?.id)
    }

    @Test
    fun `range reward wins over plus`() {
        val rewards = listOf(
            reward("plus", AmountRule.Plus(1000)),
            reward("range", AmountRule.Range(1000, 2000)),
        )

        val matched = RewardMatcher(FixedRandomSource(0)).match(rewards, 1500)

        assertEquals("range", matched?.id)
    }

    @Test
    fun `highest matching plus minimum wins`() {
        val rewards = listOf(
            reward("100-plus", AmountRule.Plus(100)),
            reward("1000-plus", AmountRule.Plus(1000)),
        )

        assertEquals("100-plus", RewardMatcher(FixedRandomSource(0)).match(rewards, 999)?.id)
        assertEquals("1000-plus", RewardMatcher(FixedRandomSource(0)).match(rewards, 1000)?.id)
        assertEquals("1000-plus", RewardMatcher(FixedRandomSource(0)).match(rewards, 5000)?.id)
    }

    @Test
    fun `chance less than or equal to zero is excluded`() {
        val rewards = listOf(
            reward("disabled", AmountRule.Exact(1000), chance = 0),
            reward("enabled", AmountRule.Exact(1000), chance = 1),
        )

        val matched = RewardMatcher(FixedRandomSource(0)).match(rewards, 1000)

        assertEquals("enabled", matched?.id)
    }

    @Test
    fun `same priority rewards use normalized weights`() {
        val rewards = listOf(
            reward("first", AmountRule.Plus(1000), chance = 70),
            reward("second", AmountRule.Plus(1000), chance = 30),
        )

        val first = RewardMatcher(FixedRandomSource(69)).match(rewards, 1000)
        val second = RewardMatcher(FixedRandomSource(70)).match(rewards, 1000)

        assertEquals("first", first?.id)
        assertEquals("second", second?.id)
    }

    @Test
    fun `returns null when nothing matches`() {
        val matched = RewardMatcher(FixedRandomSource(0)).match(
            rewards = listOf(reward("too-high", AmountRule.Exact(5000))),
            amount = 1000,
        )

        assertNull(matched)
    }

    private fun reward(
        id: String,
        amountRule: AmountRule,
        chance: Int = 100,
    ): Reward {
        return Reward(
            id = id,
            amountRule = amountRule,
            chance = chance,
            actions = listOf(mapOf("type" to "broadcast")),
        )
    }

    private class FixedRandomSource(private val value: Long) : RandomSource {
        override fun nextLong(boundExclusive: Long): Long {
            require(value < boundExclusive)
            return value
        }
    }
}
