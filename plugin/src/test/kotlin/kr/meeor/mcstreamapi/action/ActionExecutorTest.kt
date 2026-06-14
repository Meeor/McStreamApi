package kr.meeor.mcstreamapi.action

import kr.meeor.mcstreamapi.placeholder.PlaceholderContext
import kr.meeor.mcstreamapi.placeholder.PlaceholderResolver
import kr.meeor.mcstreamapi.placeholder.RandomAmount
import kr.meeor.mcstreamapi.placeholder.RandomEntry
import kr.meeor.mcstreamapi.placeholder.RandomResolver
import kr.meeor.mcstreamapi.placeholder.RandomTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionExecutorTest {
    @Test
    fun `continues after failed action`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(platform)

        val results = executor.execute(
            context = ActionContext(playerName = "Steve", rewardId = "reward"),
            actions = listOf(
                Action.Give(ActionTarget("Alex"), "DIAMOND", ActionQuantity.Fixed(1), null, emptyList()),
                Action.Broadcast("done"),
            ),
        )

        assertFalse(results[0].success)
        assertTrue(results[1].success)
        assertEquals(listOf("done"), platform.broadcasts)
    }

    @Test
    fun `replaces self target in command and give action`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(platform)

        val results = executor.execute(
            context = ActionContext(playerName = "Steve", rewardId = "reward"),
            actions = listOf(
                Action.Command("effect give @s speed"),
                Action.Give(ActionTarget("@s"), "DIAMOND", ActionQuantity.Fixed(1), null, emptyList()),
            ),
        )

        assertTrue(results.all { it.success })
        assertEquals(listOf("effect give Steve speed"), platform.commands)
        assertEquals(listOf("Steve:DIAMOND:1"), platform.givenItems)
    }

    @Test
    fun `at player command summon and sound run at target player`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(platform)

        val results = executor.execute(
            context = ActionContext(playerName = "Steve", rewardId = "reward"),
            actions = listOf(
                Action.AtPlayerCommand("summon minecraft:chicken ~ ~ ~"),
                Action.Summon(ActionTarget("@s"), "minecraft:zombie"),
                Action.Sound(ActionTarget("@s"), "minecraft:entity.player.levelup", "master", 1.0, 1.2),
            ),
        )

        assertTrue(results.all { it.success })
        assertEquals(
            listOf(
                "execute at Steve run summon minecraft:chicken ~ ~ ~",
                "execute at Steve run summon minecraft:zombie ~ ~ ~",
                "execute at Steve run playsound minecraft:entity.player.levelup master Steve ~ ~ ~ 1.0 1.2",
            ),
            platform.commands,
        )
    }

    @Test
    fun `summon uses random amount only for entity random placeholders`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(
                RandomResolver(
                    RandomTable(
                        mapOf(
                            "mob" to listOf(RandomEntry("minecraft:zombie", amount = RandomAmount.Fixed(3))),
                            "item" to listOf(RandomEntry("DIAMOND", amount = RandomAmount.Fixed(5))),
                        ),
                    ),
                ),
            ),
        )
        val context = ActionContext(
            playerName = "Steve",
            rewardId = "reward",
            placeholderContext = placeholderContext(),
        )

        val results = executor.execute(
            context = context,
            actions = listOf(
                Action.Summon(ActionTarget("@s"), "{random.mob}"),
                Action.Give(ActionTarget("@s"), "{random.item}", ActionQuantity.Fixed(1), null, emptyList()),
            ),
        )

        assertTrue(results.all { it.success })
        assertEquals(
            listOf(
                "execute at Steve run summon minecraft:zombie ~ ~ ~",
                "execute at Steve run summon minecraft:zombie ~ ~ ~",
                "execute at Steve run summon minecraft:zombie ~ ~ ~",
            ),
            platform.commands,
        )
        assertEquals(listOf("Steve:DIAMOND:1"), platform.givenItems)
    }

    @Test
    fun `custom give can select custom item key from random placeholder`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(
                RandomResolver(
                    RandomTable(
                        mapOf(
                            "custom_item" to listOf(
                                RandomEntry("{item.rare_emerald}", amount = RandomAmount.Fixed(9)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val result = ActionParser(
            customItems = mapOf(
                "rare_emerald" to mapOf(
                    "material" to "EMERALD",
                    "amount" to 2,
                    "name" to "&aRare {donator}",
                ),
            ),
        ).parse(
            listOf(
                mapOf(
                    "type" to "custom-give",
                    "target" to "@s",
                    "item" to "{random.custom_item}",
                ),
            ),
        )

        val results = executor.execute(
            context = ActionContext(
                playerName = "Steve",
                rewardId = "reward",
                placeholderContext = placeholderContext(),
            ),
            actions = result.actions,
        )

        assertTrue(results.single().success)
        assertEquals(listOf("Steve:EMERALD:2"), platform.givenItems)
    }

    @Test
    fun `custom give rejects bare custom item key from random placeholder`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(
                RandomResolver(
                    RandomTable(
                        mapOf(
                            "custom_item" to listOf(RandomEntry("rare_emerald")),
                        ),
                    ),
                ),
            ),
        )
        val result = ActionParser(
            customItems = mapOf(
                "rare_emerald" to mapOf(
                    "material" to "EMERALD",
                    "amount" to 2,
                ),
            ),
        ).parse(
            listOf(
                mapOf(
                    "type" to "custom-give",
                    "target" to "@s",
                    "item" to "{random.custom_item}",
                ),
            ),
        )

        val results = executor.execute(
            context = ActionContext(
                playerName = "Steve",
                rewardId = "reward",
                placeholderContext = placeholderContext(),
            ),
            actions = result.actions,
        )

        assertEquals("INVALID_CUSTOM_ITEM_REFERENCE", results.single().message)
        assertEquals(emptyList(), platform.givenItems)
    }

    @Test
    fun `custom item amount placeholder uses same cached amount as custom give`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(),
        )
        val customItems = mapOf(
            "random_box" to mapOf(
                "material" to "CHEST",
                "amount" to "<2..4>",
                "name" to "&6랜덤 상자",
            ),
        )
        val result = ActionParser(customItems).parse(
            listOf(
                mapOf(
                    "type" to "broadcast",
                    "message" to "{item.random_box.name}:{item.random_box.amount}",
                ),
                mapOf(
                    "type" to "custom-give",
                    "target" to "@s",
                    "item" to "{item.random_box}",
                ),
            ),
        )

        val results = executor.execute(
            context = ActionContext(
                playerName = "Steve",
                rewardId = "reward",
                placeholderContext = placeholderContext(),
                customItems = customItems,
            ),
            actions = result.actions,
        )

        assertTrue(results.all { it.success })
        val broadcastAmount = platform.broadcasts.single().substringAfter(":").toInt()
        val givenAmount = platform.givenItems.single().substringAfterLast(":").toInt()
        assertEquals("&6랜덤 상자:$broadcastAmount", platform.broadcasts.single())
        assertEquals(broadcastAmount, givenAmount)
    }

    @Test
    fun `random custom item name and amount placeholders use selected custom item`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(
                RandomResolver(
                    RandomTable(
                        mapOf(
                            "reward_item" to listOf(RandomEntry("{item.random_box}")),
                        ),
                    ),
                ),
            ),
        )
        val customItems = mapOf(
            "random_box" to mapOf(
                "material" to "CHEST",
                "amount" to "<2..4>",
                "name" to "&6랜덤 상자",
            ),
        )
        val result = ActionParser(customItems).parse(
            listOf(
                mapOf(
                    "type" to "broadcast",
                    "message" to "{random.reward_item.item.name}:{random.reward_item.item.amount}",
                ),
                mapOf(
                    "type" to "custom-give",
                    "target" to "@s",
                    "item" to "{random.reward_item}",
                ),
            ),
        )

        val results = executor.execute(
            context = ActionContext(
                playerName = "Steve",
                rewardId = "reward",
                placeholderContext = placeholderContext(),
                customItems = customItems,
            ),
            actions = result.actions,
        )

        assertTrue(results.all { it.success })
        val broadcastAmount = platform.broadcasts.single().substringAfter(":").toInt()
        val givenAmount = platform.givenItems.single().substringAfterLast(":").toInt()
        assertEquals("&6랜덤 상자:$broadcastAmount", platform.broadcasts.single())
        assertEquals(broadcastAmount, givenAmount)
    }

    @Test
    fun `offline target fails without executing target action`() {
        val platform = FakeActionPlatform(onlinePlayers = emptySet())
        val executor = ActionExecutor(platform)

        val results = executor.execute(
            context = ActionContext(playerName = "Steve", rewardId = "reward"),
            actions = listOf(Action.Title(ActionTarget("@s"), "title", null, 10, 70, 20)),
        )

        assertEquals("PLAYER_OFFLINE", results.single().message)
        assertEquals(emptyList(), platform.titles)
    }

    @Test
    fun `chat action sends private message instead of player chat`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(platform)

        val results = executor.execute(
            context = ActionContext(playerName = "Steve", rewardId = "reward"),
            actions = listOf(Action.Chat("hello")),
        )

        assertTrue(results.single().success)
        assertEquals(listOf("Steve:hello"), platform.privateMessages)
    }

    private class FakeActionPlatform(
        private val onlinePlayers: Set<String>,
    ) : ActionPlatform {
        val commands = mutableListOf<String>()
        val givenItems = mutableListOf<String>()
        val broadcasts = mutableListOf<String>()
        val privateMessages = mutableListOf<String>()
        val titles = mutableListOf<String>()

        override fun runOnMainThread(block: () -> List<ActionExecutionResult>): List<ActionExecutionResult> = block()

        override fun dispatchConsoleCommand(command: String): Boolean {
            commands.add(command)
            return true
        }

        override fun isPlayerOnline(playerName: String): Boolean = playerName in onlinePlayers

        override fun giveItem(
            playerName: String,
            material: String,
            amount: Int,
            name: String?,
            lore: List<String>,
            meta: GiveItemMeta,
        ): ActionExecutionResult {
            givenItems.add("$playerName:$material:$amount")
            return ActionExecutionResult.success("give")
        }

        override fun sendPrivateMessage(playerName: String, message: String): Boolean {
            if (playerName !in onlinePlayers) {
                return false
            }
            privateMessages.add("$playerName:$message")
            return true
        }

        override fun broadcast(message: String) {
            broadcasts.add(message)
        }

        override fun sendTitle(
            playerName: String,
            title: String,
            subtitle: String?,
            fadeInTicks: Int,
            stayTicks: Int,
            fadeOutTicks: Int,
        ) {
            titles.add("$playerName:$title")
        }
    }

    private fun placeholderContext(): PlaceholderContext {
        return PlaceholderContext(
            playerName = "Steve",
            streamerName = "Streamer",
            platform = "chzzk",
            donatorName = "Donator",
            amount = 1000,
            message = "hello",
            rewardId = "reward",
        )
    }
}
