package kr.meeor.mcstreamapi.action

import kr.meeor.mcstreamapi.placeholder.PlaceholderContext
import kr.meeor.mcstreamapi.placeholder.PlaceholderResolver
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
                    rewardId = "reward",
                ),
            ),
            actions = listOf(
                Action.Command("say {donator} {amount} @s"),
                Action.Broadcast("{streamer}:{platform}"),
            ),
        )

        assertTrue(results.all { it.success })
        assertEquals(listOf("say Donator 5000 Steve"), platform.commands)
        assertEquals(listOf("Streamer:soop"), platform.broadcasts)
    }

    private class FakeActionPlatform(
        private val onlinePlayers: Set<String>,
    ) : ActionPlatform {
        val commands = mutableListOf<String>()
        val broadcasts = mutableListOf<String>()

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
        ): ActionExecutionResult = ActionExecutionResult.success("give")

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
