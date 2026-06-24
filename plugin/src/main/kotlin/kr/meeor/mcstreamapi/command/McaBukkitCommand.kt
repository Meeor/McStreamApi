package kr.meeor.mcstreamapi.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent

class McaBukkitCommand(
    private val plugin: JavaPlugin,
    private val service: McaCommandService,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val result = service.execute(sender.toMcaSender(), args.toList())
        result.consoleLog?.let { plugin.logger.info(it) }
        if (result.clickUrl != null && sender is Player) {
            sender.sendMessage(result.message)
            sender.spigot().sendMessage(
                TextComponent("[연결하러가기]").apply {
                    color = ChatColor.BLUE
                    clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, result.clickUrl)
                },
            )
        } else {
            sender.sendMessage(result.message)
        }
        return result.handled
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = service.complete(sender.toMcaSender(), args.toList())

    private fun CommandSender.toMcaSender(): McaCommandSender {
        return McaCommandSender(
            name = name,
            uuid = (this as? Player)?.uniqueId?.toString(),
            type = if (this is Player) SenderType.PLAYER else SenderType.CONSOLE,
            hasPermission = ::hasPermission,
            notify = { message ->
                plugin.server.scheduler.runTask(plugin, Runnable { sendMessage(message) })
            },
        )
    }
}
