package kr.meeor.mcstreamapi.auth

import org.bukkit.plugin.java.JavaPlugin

interface PairingScheduler {
    fun repeatAsync(
        initialDelayTicks: Long,
        periodTicks: Long,
        task: () -> Unit,
    ): CancellableTask
}

fun interface CancellableTask {
    fun cancel()
}

class BukkitPairingScheduler(
    private val plugin: JavaPlugin,
) : PairingScheduler {
    override fun repeatAsync(
        initialDelayTicks: Long,
        periodTicks: Long,
        task: () -> Unit,
    ): CancellableTask {
        val scheduledTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable(task),
            initialDelayTicks,
            periodTicks,
        )
        return CancellableTask { scheduledTask.cancel() }
    }
}
