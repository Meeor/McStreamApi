package kr.meeor.mcstreamapi.action

import java.util.concurrent.ThreadLocalRandom

sealed class ActionQuantity {
    abstract fun resolve(): Int

    data class Fixed(val value: Int) : ActionQuantity() {
        override fun resolve(): Int = value
    }

    data class Range(val minimum: Int, val maximum: Int) : ActionQuantity() {
        override fun resolve(): Int {
            return ThreadLocalRandom.current().nextInt(minimum, maximum + 1)
        }
    }

    companion object {
        private val FIXED_PATTERN = Regex("""^\d+$""")
        private val RANGE_PATTERN = Regex("""^(\d*)\.\.(\d+)$""")

        fun parse(value: Any?, default: Int): Result<ActionQuantity> {
            if (value == null) {
                return Result.success(Fixed(default))
            }

            if (value is Number) {
                return fixed(value.toInt())
            }

            val text = normalize(value)
            return when {
                FIXED_PATTERN.matches(text) -> fixed(text.toInt())
                RANGE_PATTERN.matches(text) -> range(text)
                else -> Result.failure(IllegalArgumentException("Invalid action quantity: $value"))
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

        private fun fixed(value: Int): Result<ActionQuantity> {
            return if (value > 0) {
                Result.success(Fixed(value))
            } else {
                Result.failure(IllegalArgumentException("Quantity must be greater than zero."))
            }
        }

        private fun range(value: String): Result<ActionQuantity> {
            val match = RANGE_PATTERN.matchEntire(value)
                ?: return Result.failure(IllegalArgumentException("Invalid action quantity range: $value"))
            val minimum = match.groupValues[1].ifBlank { "1" }.toInt()
            val maximum = match.groupValues[2].toInt()
            return if (minimum > 0 && maximum >= minimum) {
                Result.success(Range(minimum, maximum))
            } else {
                Result.failure(IllegalArgumentException("Invalid action quantity range: $value"))
            }
        }
    }
}
