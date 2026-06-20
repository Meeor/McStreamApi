package kr.meeor.mcstreamapi.action

import kr.meeor.mcstreamapi.placeholder.PlaceholderContext
import kr.meeor.mcstreamapi.placeholder.PlaceholderResolver
import kr.meeor.mcstreamapi.placeholder.RandomEntry
import kr.meeor.mcstreamapi.placeholder.RandomResolver
import kr.meeor.mcstreamapi.placeholder.RandomTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionExecutorPlaceholderTest {
    @Test
    fun `resolves placeholders before action execution`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(),
        )

        val results = executor.execute(
            context = ActionContext(
                playerName = "Steve",
                rewardId = "reward",
                placeholderContext = PlaceholderContext(
                    playerName = "Steve",
                    streamerName = "Streamer",
                    platform = "soop",
                    donatorName = "Donator",
                    amount = 5000,
                    unitCount = 50,
                    rewardId = "reward",
                ),
            ),
            actions = listOf(
                Action.Command("say {donator} {amount} @s"),
                Action.Give(
                    target = ActionTarget("@s"),
                    material = "DIAMOND",
                    amount = ActionQuantity.Dynamic("{unit_count}"),
                    name = null,
                    lore = emptyList(),
                ),
                Action.Give(
                    target = ActionTarget("@s"),
                    material = "EMERALD",
                    amount = ActionQuantity.Dynamic("{unit_count+5}"),
                    name = null,
                    lore = emptyList(),
                ),
                Action.Give(
                    target = ActionTarget("@s"),
                    material = "GOLD_INGOT",
                    amount = ActionQuantity.Dynamic("{unit_count-1}"),
                    name = null,
                    lore = emptyList(),
                ),
                Action.Broadcast("{streamer}:{platform}"),
            ),
        )

        assertTrue(results.all { it.success })
        assertEquals(listOf("say Donator 5000 Steve"), platform.commands)
        assertEquals(listOf(50, 55, 49), platform.giveAmounts)
        assertEquals(listOf("Streamer:soop"), platform.broadcasts)
    }

    @Test
    fun `localizes random game values only in player visible messages`() {
        val platform = FakeActionPlatform(onlinePlayers = setOf("Steve"))
        val executor = ActionExecutor(
            platform = platform,
            placeholderResolver = PlaceholderResolver(
                RandomResolver(RandomTable(mapOf("mob" to listOf(RandomEntry("zombie"))))),
            ),
        )
        val context = ActionContext(
            playerName = "Steve",
            rewardId = "reward",
            placeholderContext = PlaceholderContext(
                playerName = "Steve",
                streamerName = "Streamer",
                platform = "soop",
                donatorName = "Donator",
                amount = 1000,
                rewardId = "reward",
            ),
        )

        executor.execute(
            context,
            listOf(
                Action.Command("say {random.mob}"),
                Action.Broadcast("{random.mob} 등장"),
            ),
        )

        assertEquals(listOf("say zombie"), platform.commands)
        assertEquals(listOf("<zombie> 등장"), platform.broadcasts)
    }

    private class FakeActionPlatform(
        private val onlinePlayers: Set<String>,
    ) : ActionPlatform {
        val commands = mutableListOf<String>()
        val broadcasts = mutableListOf<String>()
        val giveAmounts = mutableListOf<Int>()

        override fun runOnMainThread(block: () -> List<ActionExecutionResult>): List<ActionExecutionResult> = block()

        override fun dispatchConsoleCommand(command: String): Boolean {
            commands.add(command)
            return true
        }

        override fun isPlayerOnline(playerName: String): Boolean = playerName in onlinePlayers

        override fun localizeGameValue(value: String): String = "<$value>"

        override fun giveItem(
            playerName: String,
            material: String,
            amount: Int,
            name: String?,
            lore: List<String>,
            meta: GiveItemMeta,
        ): ActionExecutionResult {
            giveAmounts.add(amount)
            return ActionExecutionResult.success("give")
        }

        override fun sendPrivateMessage(playerName: String, message: String): Boolean = true

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
        ) = Unit
    }
}
