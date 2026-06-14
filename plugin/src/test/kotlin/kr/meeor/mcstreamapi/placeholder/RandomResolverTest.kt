package kr.meeor.mcstreamapi.placeholder

import kr.meeor.mcstreamapi.reward.RandomSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RandomResolverTest {
    @Test
    fun `normalizes weights even when chance sum is not 100`() {
        val resolver = RandomResolver(
            table = RandomTable(
                mapOf(
                    "item" to listOf(
                        RandomEntry("first", 70),
                        RandomEntry("second", 30),
                    ),
                ),
            ),
            randomSource = FixedRandomSource(70),
        )

        assertEquals("second", resolver.resolve("item"))
    }

    @Test
    fun `excludes chance less than or equal to zero`() {
        val resolver = RandomResolver(
            table = RandomTable(
                mapOf(
                    "item" to listOf(
                        RandomEntry("disabled", 0),
                        RandomEntry("enabled", 1),
                    ),
                ),
            ),
            randomSource = FixedRandomSource(0),
        )

        assertEquals("enabled", resolver.resolve("item"))
    }

    @Test
    fun `returns null for unknown or empty random keys`() {
        val resolver = RandomResolver(RandomTable(emptyMap()), FixedRandomSource(0))

        assertNull(resolver.resolve("missing"))
    }

    @Test
    fun `uses latest table from table provider`() {
        var table = RandomTable(mapOf("item" to listOf(RandomEntry("first"))))
        val resolver = RandomResolver(tableProvider = { table }, randomSource = FixedRandomSource(0))

        assertEquals("first", resolver.resolve("item"))

        table = RandomTable(mapOf("item" to listOf(RandomEntry("second"))))

        assertEquals("second", resolver.resolve("item"))
    }

    private class FixedRandomSource(private val value: Long) : RandomSource {
        override fun nextLong(boundExclusive: Long): Long {
            require(value < boundExclusive)
            return value
        }
    }
}
