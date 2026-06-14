package kr.meeor.mcstreamapi.session

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class BukkitOnlinePlayerRegistry(
    private val plugin: JavaPlugin,
) : OnlinePlayerRegistry {
    override fun isOnline(playerUuid: String): Boolean {
        return plugin.server.getPlayer(UUID.fromString(playerUuid))?.isOnline == true
    }

    override fun notify(playerUuid: String, message: String) {
        plugin.server.scheduler.runTask(
            plugin,
            Runnable { plugin.server.getPlayer(UUID.fromString(playerUuid))?.sendMessage(message) },
        )
    }
}
