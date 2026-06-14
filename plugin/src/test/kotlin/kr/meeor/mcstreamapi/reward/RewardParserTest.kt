package kr.meeor.mcstreamapi.reward

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RewardParserTest {
    @Test
    fun `parses valid rewards and disables invalid rewards`() {
        val result = RewardParser().parse(
            platform = "chzzk",
            rawRewards = listOf(
                mapOf(
                    "id" to "default_chance",
                    "amount" to "1000",
                    "actions" to listOf(mapOf("type" to "broadcast")),
                ),
                mapOf(
                    "id" to "invalid_amount",
                    "amount" to "abc",
                    "actions" to listOf(mapOf("type" to "broadcast")),
                ),
                mapOf(
                    "id" to "missing_actions",
                    "amount" to "5000+",
                ),
            ),
        )

        assertEquals(1, result.rewards.size)
        assertEquals(Reward.DEFAULT_CHANCE, result.rewards.single().chance)
        assertIs<AmountRule.Exact>(result.rewards.single().amountRule)
        assertEquals(listOf("invalid_amount", "missing_actions"), result.disabledRewards.map { it.id })
    }
}
