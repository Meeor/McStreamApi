package kr.meeor.mcstreamapi.reward

data class RewardLoadResult(
    val rewards: List<Reward>,
    val disabledRewards: List<DisabledReward>,
)

data class DisabledReward(
    val id: String,
    val reason: String,
)
