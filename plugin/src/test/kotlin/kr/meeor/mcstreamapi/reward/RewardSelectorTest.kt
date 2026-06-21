package kr.meeor.mcstreamapi.reward

import kotlin.test.Test
import kotlin.test.assertEquals

class RewardSelectorTest {
    private val selector = RewardSelector(rewardMatcher = RewardMatcher(FixedRandomSource))

    @Test
    fun `matching streamer reward wins over default reward`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = listOf(reward("streamer", "1000")),
            defaultRewards = listOf(reward("default", "1000")),
        )

        assertEquals("streamer", selected?.reward?.id)
        assertEquals(RewardSource.STREAMER, selected?.source)
    }

    @Test
    fun `default exact reward wins over streamer plus reward`() {
        val selected = selector.select(
            platform = "soop",
            amount = 10000,
            streamerRewards = listOf(reward("streamer-plus", "100+")),
            defaultRewards = listOf(reward("default-exact", "10000")),
        )

        assertEquals("default-exact", selected?.reward?.id)
        assertEquals(RewardSource.DEFAULT, selected?.source)
    }

    @Test
    fun `default exact reward wins over streamer range reward`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = listOf(reward("streamer-range", "500-1500")),
            defaultRewards = listOf(reward("default-exact", "1000")),
        )

        assertEquals("default-exact", selected?.reward?.id)
        assertEquals(RewardSource.DEFAULT, selected?.source)
    }

    @Test
    fun `streamer range reward wins over default range reward`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = listOf(reward("streamer-range", "100-2000")),
            defaultRewards = listOf(reward("default-range", "500-1500")),
        )

        assertEquals("streamer-range", selected?.reward?.id)
        assertEquals(RewardSource.STREAMER, selected?.source)
    }

    @Test
    fun `default range reward wins over streamer plus reward`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = listOf(reward("streamer-plus", "100+")),
            defaultRewards = listOf(reward("default-range", "500-1500")),
        )

        assertEquals("default-range", selected?.reward?.id)
        assertEquals(RewardSource.DEFAULT, selected?.source)
    }

    @Test
    fun `streamer plus reward wins over default plus reward`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = listOf(reward("streamer-plus", "100+")),
            defaultRewards = listOf(reward("default-plus", "100+")),
        )

        assertEquals("streamer-plus", selected?.reward?.id)
        assertEquals(RewardSource.STREAMER, selected?.source)
    }

    @Test
    fun `default reward is used when streamer reward does not match`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = listOf(reward("streamer", "5000")),
            defaultRewards = listOf(reward("default", "1000")),
        )

        assertEquals("default", selected?.reward?.id)
        assertEquals(RewardSource.DEFAULT, selected?.source)
    }

    @Test
    fun `default reward is used when streamer platform is empty`() {
        val selected = selector.select(
            platform = "soop",
            amount = 1000,
            streamerRewards = emptyList(),
            defaultRewards = listOf(reward("default", "1000")),
        )

        assertEquals("default", selected?.reward?.id)
    }

    private fun reward(id: String, amount: String): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "amount" to amount,
            "actions" to listOf(mapOf("type" to "broadcast", "message" to id)),
        )
    }

    private object FixedRandomSource : RandomSource {
        override fun nextLong(boundExclusive: Long): Long = 0
    }
}
