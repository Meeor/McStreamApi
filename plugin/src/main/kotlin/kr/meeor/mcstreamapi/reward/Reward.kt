package kr.meeor.mcstreamapi.reward

data class Reward(
    val id: String,
    val amountRule: AmountRule,
    val chance: Int = DEFAULT_CHANCE,
    val actions: List<Map<String, Any?>>,
) {
    companion object {
        const val DEFAULT_CHANCE = 100
    }
}
