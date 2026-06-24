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
        return selectAll(
            platform = platform,
            amount = amount,
            streamerRewards = streamerRewards,
            defaultRewards = defaultRewards,
            onDisabled = onDisabled,
        ).firstOrNull()
    }

    fun selectAll(
        platform: String,
        amount: Long,
        streamerRewards: List<Map<String, Any?>>,
        defaultRewards: List<Map<String, Any?>>,
        onDisabled: (RewardSource, DisabledReward) -> Unit = { _, _ -> },
    ): List<RewardSelection> {
        val parsedBySource = listOf(
            RewardSource.STREAMER to streamerRewards,
            RewardSource.DEFAULT to defaultRewards,
        ).map { (source, rawRewards) ->
            val parsed = rewardParser.parse(platform, rawRewards)
            parsed.disabledRewards.forEach { onDisabled(source, it) }
            source to parsed.rewards
        }

        val selections = mutableListOf<RewardSelection>()
        var streamerDuplicateAllowed = false
        for (priority in listOf(EXACT_PRIORITY, RANGE_PRIORITY, PLUS_PRIORITY)) {
            for ((source, rewards) in parsedBySource) {
                if (source == RewardSource.STREAMER && streamerDuplicateAllowed) {
                    continue
                }
                val samePriority = rewards.filter { it.amountRule.priority == priority }
                val reward = rewardMatcher.match(samePriority, amount) ?: continue
                val selection = RewardSelection(reward, source)
                selections.add(selection)
                if (source == RewardSource.STREAMER && reward.allowDuplicate) {
                    streamerDuplicateAllowed = true
                    continue
                }
                return selections
            }
        }
        return selections
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
