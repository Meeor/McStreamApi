package kr.meeor.mcstreamapi.reward

sealed class AmountRule(
    val priority: Int,
) {
    abstract fun matches(amount: Long): Boolean

    data class Exact(val amount: Long) : AmountRule(priority = 3) {
        override fun matches(amount: Long): Boolean = this.amount == amount
    }

    data class Range(val minimum: Long, val maximum: Long) : AmountRule(priority = 2) {
        override fun matches(amount: Long): Boolean = amount in minimum..maximum
    }

    data class Plus(val minimum: Long) : AmountRule(priority = 1) {
        override fun matches(amount: Long): Boolean = amount >= minimum
    }

    companion object {
        private val EXACT_PATTERN = Regex("""^\d+$""")
        private val RANGE_PATTERN = Regex("""^(\d+)\s*-\s*(\d+)$""")
        private val PLUS_PATTERN = Regex("""^(\d+)\+$""")

        fun parse(value: String): Result<AmountRule> {
            val normalized = value.trim()
            return when {
                EXACT_PATTERN.matches(normalized) -> parseExact(normalized)
                RANGE_PATTERN.matches(normalized) -> parseRange(normalized)
                PLUS_PATTERN.matches(normalized) -> parsePlus(normalized)
                else -> Result.failure(IllegalArgumentException("Invalid amount rule: $value"))
            }
        }

        private fun parseExact(value: String): Result<AmountRule> {
            val amount = value.toLong()
            return if (amount > 0) {
                Result.success(Exact(amount))
            } else {
                Result.failure(IllegalArgumentException("Amount must be greater than zero: $value"))
            }
        }

        private fun parseRange(value: String): Result<AmountRule> {
            val match = RANGE_PATTERN.matchEntire(value) ?: error("range pattern mismatch")
            val minimum = match.groupValues[1].toLong()
            val maximum = match.groupValues[2].toLong()
            return if (minimum > 0 && maximum >= minimum) {
                Result.success(Range(minimum, maximum))
            } else {
                Result.failure(IllegalArgumentException("Invalid amount range: $value"))
            }
        }

        private fun parsePlus(value: String): Result<AmountRule> {
            val match = PLUS_PATTERN.matchEntire(value) ?: error("plus pattern mismatch")
            val minimum = match.groupValues[1].toLong()
            return if (minimum > 0) {
                Result.success(Plus(minimum))
            } else {
                Result.failure(IllegalArgumentException("Invalid plus amount: $value"))
            }
        }
    }
}
