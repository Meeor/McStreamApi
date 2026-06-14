package kr.meeor.mcstreamapi.reward

import java.util.concurrent.ThreadLocalRandom

class RewardMatcher(
    private val randomSource: RandomSource = ThreadLocalRandomSource,
) {
    fun match(rewards: List<Reward>, amount: Long): Reward? {
        val candidates = rewards
            .filter { it.chance > 0 && it.amountRule.matches(amount) }

        if (candidates.isEmpty()) {
            return null
        }

        val highestPriority = candidates.maxOf { it.amountRule.priority }
        val samePriority = candidates.filter { it.amountRule.priority == highestPriority }

        return weightedPick(samePriority)
    }

    private fun weightedPick(rewards: List<Reward>): Reward {
        val totalWeight = rewards.sumOf { it.chance.toLong() }
        var cursor = randomSource.nextLong(totalWeight)

        rewards.forEach { reward ->
            cursor -= reward.chance
            if (cursor < 0) {
                return reward
            }
        }

        return rewards.last()
    }
}

fun interface RandomSource {
    fun nextLong(boundExclusive: Long): Long
}

private object ThreadLocalRandomSource : RandomSource {
    override fun nextLong(boundExclusive: Long): Long {
        return ThreadLocalRandom.current().nextLong(boundExclusive)
    }
}
