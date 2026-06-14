package kr.meeor.mcstreamapi.placeholder

import kr.meeor.mcstreamapi.reward.RandomSource
import java.util.concurrent.ThreadLocalRandom

class RandomResolver(
    private val tableProvider: () -> RandomTable,
    private val randomSource: RandomSource = ThreadLocalRandomSource,
) {
    constructor(
        table: RandomTable,
        randomSource: RandomSource = ThreadLocalRandomSource,
    ) : this({ table }, randomSource)

    fun resolve(key: String): String? {
        return resolveEntry(key)?.value
    }

    fun resolveEntry(key: String): RandomEntry? {
        val candidates = tableProvider().entries[key]
            .orEmpty()
            .filter { it.chance > 0 }

        if (candidates.isEmpty()) {
            return null
        }

        val totalWeight = candidates.sumOf { it.chance.toLong() }
        var cursor = randomSource.nextLong(totalWeight)
        candidates.forEach { entry ->
            cursor -= entry.chance
            if (cursor < 0) {
                return entry
            }
        }

        return candidates.last()
    }
}

data class RandomTable(
    val entries: Map<String, List<RandomEntry>>,
)

data class RandomEntry(
    val value: String,
    val chance: Int = DEFAULT_CHANCE,
    val amount: RandomAmount? = null,
    val display: String? = null,
) {
    companion object {
        const val DEFAULT_CHANCE = 100
    }
}

sealed class RandomAmount {
    abstract fun resolve(): Int

    data class Fixed(val value: Int) : RandomAmount() {
        override fun resolve(): Int = value
    }

    data class Range(val minimum: Int, val maximum: Int) : RandomAmount() {
        override fun resolve(): Int {
            return ThreadLocalRandom.current().nextInt(minimum, maximum + 1)
        }
    }

    companion object {
        private val FIXED_PATTERN = Regex("""^\d+$""")
        private val RANGE_PATTERN = Regex("""^(\d*)\.\.(\d+)$""")

        fun parse(value: Any?): RandomAmount? {
            if (value == null) {
                return null
            }

            if (value is Number) {
                return fixed(value.toInt())
            }

            val text = normalize(value)
            return when {
                FIXED_PATTERN.matches(text) -> fixed(text.toInt())
                RANGE_PATTERN.matches(text) -> range(text)
                else -> null
            }
        }

        private fun normalize(value: Any?): String {
            val raw = when (value) {
                is Map<*, *> -> value.keys.firstOrNull()?.toString().orEmpty()
                else -> value?.toString().orEmpty()
            }.trim()

            return raw.removePrefix("{").removeSuffix("}").trim()
                .removePrefix("<").removeSuffix(">").trim()
        }

        private fun fixed(value: Int): RandomAmount? {
            return if (value > 0) Fixed(value) else null
        }

        private fun range(value: String): RandomAmount? {
            val match = RANGE_PATTERN.matchEntire(value) ?: return null
            val minimum = match.groupValues[1].ifBlank { "1" }.toInt()
            val maximum = match.groupValues[2].toInt()
            return if (minimum > 0 && maximum >= minimum) {
                Range(minimum, maximum)
            } else {
                null
            }
        }
    }
}

private object ThreadLocalRandomSource : RandomSource {
    override fun nextLong(boundExclusive: Long): Long {
        return ThreadLocalRandom.current().nextLong(boundExclusive)
    }
}
