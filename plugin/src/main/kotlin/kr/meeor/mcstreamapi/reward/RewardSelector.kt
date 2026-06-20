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
        val sources = listOf(
            RewardSource.STREAMER to streamerRewards,
            RewardSource.DEFAULT to defaultRewards,
        )
        for ((source, rawRewards) in sources) {
            if (rawRewards.isEmpty()) {
                continue
            }
            val parsed = rewardParser.parse(platform, rawRewards)
            parsed.disabledRewards.forEach { onDisabled(source, it) }
            val reward = rewardMatcher.match(parsed.rewards, amount) ?: continue
            return RewardSelection(reward, source)
        }
        return null
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
