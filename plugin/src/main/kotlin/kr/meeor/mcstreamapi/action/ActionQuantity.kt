package kr.meeor.mcstreamapi.action

import java.util.concurrent.ThreadLocalRandom

sealed class ActionQuantity {
    abstract fun resolve(renderer: (String) -> String = { it }): Int

    data class Fixed(val value: Int) : ActionQuantity() {
        override fun resolve(renderer: (String) -> String): Int = value
    }

    data class Range(val minimum: Int, val maximum: Int) : ActionQuantity() {
        override fun resolve(renderer: (String) -> String): Int {
            return ThreadLocalRandom.current().nextInt(minimum, maximum + 1)
        }
    }

    data class Dynamic(val template: String) : ActionQuantity() {
        override fun resolve(renderer: (String) -> String): Int {
            return resolveDynamic(template, renderer).getOrThrow()
        }
    }

    companion object {
        private val FIXED_PATTERN = Regex("""^\d+$""")
        private val RANGE_PATTERN = Regex("""^(\d*)\.\.(\d+)$""")
        private val PLACEHOLDER_PATTERN = Regex("""\{[a-zA-Z0-9_.-]+}""")
        private val QUANTITY_EXPRESSION_PATTERN = Regex("""^\{(unit_count)([+-]\d+)?}$""")

        fun parse(value: Any?, default: Int): Result<ActionQuantity> {
            if (value == null) {
                return Result.success(Fixed(default))
            }

            if (value is Number) {
                return fixed(value.toInt())
            }

            val raw = raw(value)
            if (PLACEHOLDER_PATTERN.containsMatchIn(raw) || QUANTITY_EXPRESSION_PATTERN.matches(raw)) {
                return Result.success(Dynamic(raw))
            }

            val text = normalize(value)
            return parseStatic(text)
        }

        private fun parseStatic(text: String): Result<ActionQuantity> {
            return when {
                FIXED_PATTERN.matches(text) -> text.toIntOrNull()?.let(::fixed)
                    ?: Result.failure(IllegalArgumentException("Action quantity is too large: $text"))
                RANGE_PATTERN.matches(text) -> range(text)
                else -> Result.failure(IllegalArgumentException("Invalid action quantity: $text"))
            }
        }

        private fun resolveDynamic(template: String, renderer: (String) -> String): Result<Int> {
            val expression = QUANTITY_EXPRESSION_PATTERN.matchEntire(template)
            if (expression == null) {
                return parseStatic(renderer(template)).mapCatching { it.resolve(renderer) }
            }

            val basePlaceholder = "{${expression.groupValues[1]}}"
            val base = renderer(basePlaceholder).toLongOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid quantity placeholder: $basePlaceholder"))
            val modifier = expression.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
            val value = runCatching { Math.addExact(base, modifier) }.getOrElse {
                return Result.failure(IllegalArgumentException("Action quantity overflow: $template"))
            }
            if (value !in 1..Int.MAX_VALUE.toLong()) {
                return Result.failure(IllegalArgumentException("Action quantity must be between 1 and ${Int.MAX_VALUE}: $value"))
            }
            return Result.success(value.toInt())
        }

        private fun raw(value: Any?): String {
            return when (value) {
                is Map<*, *> -> value.keys.firstOrNull()?.toString().orEmpty()
                else -> value?.toString().orEmpty()
            }.trim()
        }

        private fun normalize(value: Any?): String {
            return raw(value).removePrefix("{").removeSuffix("}").trim()
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
