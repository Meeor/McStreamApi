package kr.meeor.mcstreamapi.session

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class BukkitPlayerSessionListener(
    private val sessionManager: PlayerDonationSessionManager,
) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        sessionManager.playerJoined(
            playerUuid = event.player.uniqueId.toString(),
            playerName = event.player.name,
        )
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        sessionManager.playerQuit(event.player.uniqueId.toString(), event.player.name)
    }
}
