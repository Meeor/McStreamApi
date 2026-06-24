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
            val unitAmount = if (raw.containsKey("unitAmount")) {
                raw.long("unitAmount")?.takeIf { it > 0 } ?: run {
                    disabled.add(DisabledReward(id, "INVALID_UNIT_AMOUNT"))
                    return@forEachIndexed
                }
            } else {
                null
            }
            val hasBonusAmount = raw.containsKey("bonusAmount")
            val hasBonusCount = raw.containsKey("bonusCount")
            if (hasBonusAmount != hasBonusCount) {
                disabled.add(DisabledReward(id, "INCOMPLETE_BONUS_CONFIG"))
                return@forEachIndexed
            }
            if (hasBonusAmount && unitAmount == null) {
                disabled.add(DisabledReward(id, "MISSING_UNIT_AMOUNT_FOR_BONUS"))
                return@forEachIndexed
            }
            val bonusAmount = if (hasBonusAmount) {
                raw.long("bonusAmount")?.takeIf { it > 0 } ?: run {
                    disabled.add(DisabledReward(id, "INVALID_BONUS_AMOUNT"))
                    return@forEachIndexed
                }
            } else {
                null
            }
            val bonusCount = if (hasBonusCount) {
                raw.long("bonusCount")?.takeIf { it > 0 } ?: run {
                    disabled.add(DisabledReward(id, "INVALID_BONUS_COUNT"))
                    return@forEachIndexed
                }
            } else {
                null
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
                    unitAmount = unitAmount,
                    bonusAmount = bonusAmount,
                    bonusCount = bonusCount,
                    chance = raw.int("chance") ?: Reward.DEFAULT_CHANCE,
                    allowDuplicate = raw.boolean("allowDuplicate") ?: false,
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

    private fun Map<String, Any?>.long(key: String): Long? {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun Map<String, Any?>.boolean(key: String): Boolean? {
        return when (val value = this[key]) {
            is Boolean -> value
            is String -> when (value.trim().lowercase()) {
                "true", "yes", "y", "1" -> true
                "false", "no", "n", "0" -> false
                else -> null
            }
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
