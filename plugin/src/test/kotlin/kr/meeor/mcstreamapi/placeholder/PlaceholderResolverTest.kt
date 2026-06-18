package kr.meeor.mcstreamapi.placeholder

import kr.meeor.mcstreamapi.reward.RandomSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderResolverTest {
    @Test
    fun `resolves standard player streamer and donation placeholders`() {
        val resolver = PlaceholderResolver()

        val rendered = resolver.resolve(
            template = "{player}:{player_uuid}:{uuid}:{streamer}:{platform}:{donator}:{amount}:{message}:{reward_id}",
            context = context(),
        )

        assertEquals(
            "Steve:00000000-0000-0000-0000-000000000001:00000000-0000-0000-0000-000000000001:Streamer:chzzk:Donator:1000:hello:reward_1",
            rendered,
        )
    }

    @Test
    fun `keeps unknown placeholders unchanged`() {
        val resolver = PlaceholderResolver()

        val rendered = resolver.resolve("before {unknown.value} after", context())

        assertEquals("before {unknown.value} after", rendered)
    }

    @Test
    fun `random placeholder is cached for one event`() {
        val resolver = PlaceholderResolver(
            RandomResolver(
                table = RandomTable(mapOf("mob" to listOf(RandomEntry("zombie"), RandomEntry("skeleton")))),
                randomSource = SequenceRandomSource(0, 100),
            ),
        )
        val state = PlaceholderEventState()

        val first = resolver.resolve("{random.mob}", context(), state)
        val second = resolver.resolve("{random.mob}", context(), state)

        assertEquals("zombie", first)
        assertEquals("zombie", second)
    }

    @Test
    fun `random once placeholder resolves every call`() {
        val resolver = PlaceholderResolver(
            RandomResolver(
                table = RandomTable(mapOf("mob" to listOf(RandomEntry("zombie"), RandomEntry("skeleton")))),
                randomSource = SequenceRandomSource(0, 100),
            ),
        )
        val state = PlaceholderEventState()

        val first = resolver.resolve("{random_once.mob}", context(), state)
        val second = resolver.resolve("{random_once.mob}", context(), state)

        assertEquals("zombie", first)
        assertEquals("skeleton", second)
    }

    @Test
    fun `random display and amount use cached random selection`() {
        val resolver = PlaceholderResolver(
            RandomResolver(
                table = RandomTable(
                    mapOf(
                        "mob" to listOf(
                            RandomEntry(
                                value = "zombie",
                                amount = RandomAmount.Range(2, 4),
                                display = "좀비",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = PlaceholderEventState()

        val rendered = resolver.resolve(
            "{random.mob.display}:{random.mob.amount}:{random.mob}",
            context(),
            state,
        )
        val renderedAgain = resolver.resolve("{random.mob.amount}", context(), state)

        val parts = rendered.split(":")
        assertEquals("좀비", parts[0])
        assertEquals("zombie", parts[2])
        assertEquals(parts[1], renderedAgain)
    }

    @Test
    fun `random custom item display can be used for messages`() {
        val resolver = PlaceholderResolver(
            RandomResolver(
                table = RandomTable(
                    mapOf(
                        "reward_item" to listOf(
                            RandomEntry(
                                value = "{item.rare_emerald}",
                                display = "희귀 에메랄드",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = PlaceholderEventState()

        assertEquals("희귀 에메랄드", resolver.resolve("{random.reward_item.display}", context(), state))
        assertEquals("{item.rare_emerald}", resolver.resolve("{random.reward_item}", context(), state))
    }

    @Test
    fun `custom item name and amount placeholders resolve from custom item config`() {
        val resolver = PlaceholderResolver()
        val state = PlaceholderEventState()
        val customItems = mapOf(
            "rare_emerald" to mapOf(
                "material" to "EMERALD",
                "amount" to "<2..4>",
                "name" to "&a희귀 에메랄드",
            ),
        )

        val rendered = resolver.resolve(
            "{item.rare_emerald.name}:{item.rare_emerald.amount}",
            context(),
            state,
            customItems,
        )
        val renderedAgain = resolver.resolve("{item.rare_emerald.amount}", context(), state, customItems)

        val parts = rendered.split(":")
        assertEquals("&a희귀 에메랄드", parts[0])
        assertEquals(parts[1], renderedAgain)
    }

    private fun context(): PlaceholderContext {
        return PlaceholderContext(
            playerName = "Steve",
            playerUuid = "00000000-0000-0000-0000-000000000001",
            streamerName = "Streamer",
            platform = "chzzk",
            donatorName = "Donator",
            amount = 1000,
            message = "hello",
            rewardId = "reward_1",
        )
    }

    private class SequenceRandomSource(vararg values: Long) : RandomSource {
        private val values = values.toMutableList()

        override fun nextLong(boundExclusive: Long): Long {
            val value = values.removeFirst()
            require(value < boundExclusive)
            return value
        }
    }
}
