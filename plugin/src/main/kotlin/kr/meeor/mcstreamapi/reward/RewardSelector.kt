package kr.meeor.mcstreamapi.reward

class RewardSelector(
    private val rewardParser: RewardParser = RewardParser(),
    private val rewardMatcher: RewardMatcher = RewardMatcher(),
) {
    fun select(
        platform: String,
        amount: Long,
        streamerRewards: List<Map<String, Any?>>,
        defaultRewards: List<Map<String, Any?>>,
        onDisabled: (RewardSource, DisabledReward) -> Unit = { _, _ -> },
    ): RewardSelection? {
        val parsedBySource = listOf(
            RewardSource.STREAMER to streamerRewards,
            RewardSource.DEFAULT to defaultRewards,
        ).map { (source, rawRewards) ->
            val parsed = rewardParser.parse(platform, rawRewards)
            parsed.disabledRewards.forEach { onDisabled(source, it) }
            source to parsed.rewards
        }

        for (priority in listOf(EXACT_PRIORITY, RANGE_PRIORITY, PLUS_PRIORITY)) {
            for ((source, rewards) in parsedBySource) {
                val samePriority = rewards.filter { it.amountRule.priority == priority }
                val reward = rewardMatcher.match(samePriority, amount) ?: continue
                return RewardSelection(reward, source)
            }
        }
        return null
    }

    private companion object {
        const val EXACT_PRIORITY = 3
        const val RANGE_PRIORITY = 2
        const val PLUS_PRIORITY = 1
    }
}

data class RewardSelection(
    val reward: Reward,
    val source: RewardSource,
)

enum class RewardSource {
    STREAMER,
    DEFAULT,
}
