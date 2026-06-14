package kr.meeor.mcstreamapi.reward

class RewardParser {
    fun parse(platform: String, rawRewards: List<Map<String, Any?>>): RewardLoadResult {
        val rewards = mutableListOf<Reward>()
        val disabled = mutableListOf<DisabledReward>()

        rawRewards.forEachIndexed { index, raw ->
            val id = raw.string("id") ?: "$platform#$index"
            val amountText = raw.string("amount")
            if (amountText == null) {
                disabled.add(DisabledReward(id, "MISSING_AMOUNT"))
                return@forEachIndexed
            }

            val amountRule = AmountRule.parse(amountText).getOrElse {
                disabled.add(DisabledReward(id, "INVALID_AMOUNT"))
                return@forEachIndexed
            }

            val actions = raw.actions()
            if (actions.isEmpty()) {
                disabled.add(DisabledReward(id, "MISSING_ACTIONS"))
                return@forEachIndexed
            }

            rewards.add(
                Reward(
                    id = id,
                    amountRule = amountRule,
                    chance = raw.int("chance") ?: Reward.DEFAULT_CHANCE,
                    actions = actions,
                ),
            )
        }

        return RewardLoadResult(rewards = rewards, disabledRewards = disabled)
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key]?.toString()?.trim()

    private fun Map<String, Any?>.int(key: String): Int? {
        return when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun Map<String, Any?>.actions(): List<Map<String, Any?>> {
        val value = this["actions"] as? List<*> ?: return emptyList()
        return value.mapNotNull { action ->
            @Suppress("UNCHECKED_CAST")
            action as? Map<String, Any?>
        }
    }
}
