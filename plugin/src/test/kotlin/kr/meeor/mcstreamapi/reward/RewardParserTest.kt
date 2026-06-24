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
                    "unitAmount" to 100,
                    "bonusAmount" to 1000,
                    "bonusCount" to 1,
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
                mapOf(
                    "id" to "invalid_unit_amount",
                    "amount" to "100+",
                    "unitAmount" to 0,
                    "actions" to listOf(mapOf("type" to "broadcast")),
                ),
                mapOf(
                    "id" to "incomplete_bonus",
                    "amount" to "100+",
                    "unitAmount" to 100,
                    "bonusAmount" to 1000,
                    "actions" to listOf(mapOf("type" to "broadcast")),
                ),
            ),
        )

        assertEquals(1, result.rewards.size)
        assertEquals(Reward.DEFAULT_CHANCE, result.rewards.single().chance)
        assertEquals(false, result.rewards.single().allowDuplicate)
        assertEquals(100, result.rewards.single().unitAmount)
        assertEquals(1000, result.rewards.single().bonusAmount)
        assertEquals(1, result.rewards.single().bonusCount)
        assertEquals(11, result.rewards.single().unitCount(1000))
        assertEquals(55, result.rewards.single().unitCount(5000))
        assertEquals(110, result.rewards.single().unitCount(10000))
        assertIs<AmountRule.Exact>(result.rewards.single().amountRule)
        assertEquals(
            listOf("invalid_amount", "missing_actions", "invalid_unit_amount", "incomplete_bonus"),
            result.disabledRewards.map { it.id },
        )
    }

    @Test
    fun `parses allow duplicate option`() {
        val result = RewardParser().parse(
            platform = "soop",
            rawRewards = listOf(
                mapOf(
                    "id" to "message_only",
                    "amount" to "100+",
                    "allowDuplicate" to true,
                    "actions" to listOf(mapOf("type" to "broadcast")),
                ),
            ),
        )

        assertEquals(true, result.rewards.single().allowDuplicate)
    }
}
