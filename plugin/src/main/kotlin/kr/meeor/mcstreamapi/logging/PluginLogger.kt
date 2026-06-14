package kr.meeor.mcstreamapi.logging

import java.util.logging.Level
import java.util.logging.Logger

class PluginLogger(
    private val delegate: Logger,
    private val masker: LogMasker = LogMasker(),
    private val debugEnabled: () -> Boolean = { false },
) {
    fun info(message: String) {
        delegate.info(format(message))
    }

    fun debug(message: String) {
        if (debugEnabled()) {
            delegate.info(format("§7[DEBUG] $message"))
        }
    }

    fun warning(message: String) {
        delegate.warning(format(message))
    }

    fun error(message: String, throwable: Throwable? = null) {
        val throwableType = throwable?.javaClass?.simpleName
        val throwableMessage = throwable?.message?.takeIf { it.isNotBlank() }
        val safeMessage = if (throwableType == null) {
            message
        } else if (throwableMessage == null) {
            "$message exceptionType=$throwableType"
        } else {
            "$message exceptionType=$throwableType exceptionMessage=$throwableMessage"
        }
        delegate.log(Level.SEVERE, format(safeMessage))
    }

    private fun format(message: String): String {
        return colorize(masker.mask(message))
    }

    private fun colorize(message: String): String {
        return message
            .replace("§a", "\u001B[32m")
            .replace("§b", "\u001B[36m")
            .replace("§c", "\u001B[31m")
            .replace("§e", "\u001B[33m")
            .replace("§7", "\u001B[90m")
            .replace("§f", "\u001B[37m") + "\u001B[0m"
    }
}
