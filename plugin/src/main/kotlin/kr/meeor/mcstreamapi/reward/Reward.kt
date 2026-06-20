package kr.meeor.mcstreamapi.reward

data class Reward(
    val id: String,
    val amountRule: AmountRule,
    val unitAmount: Long? = null,
    val bonusAmount: Long? = null,
    val bonusCount: Long? = null,
    val chance: Int = DEFAULT_CHANCE,
    val actions: List<Map<String, Any?>>,
) {
    fun unitCount(amount: Long): Long {
        val unit = unitAmount ?: return 1L
        val baseCount = amount / unit
        val bonusUnit = bonusAmount ?: return baseCount
        val countPerBonus = bonusCount ?: return baseCount
        val bonusUnits = amount / bonusUnit
        val bonus = saturatingMultiply(bonusUnits, countPerBonus)
        return saturatingAdd(baseCount, bonus)
    }

    private fun saturatingMultiply(left: Long, right: Long): Long {
        return runCatching { Math.multiplyExact(left, right) }.getOrDefault(Long.MAX_VALUE)
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        return runCatching { Math.addExact(left, right) }.getOrDefault(Long.MAX_VALUE)
    }

    companion object {
        const val DEFAULT_CHANCE = 100
    }
}
