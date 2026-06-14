package kr.meeor.mcstreamapi.command

data class McaCommandSender(
    val name: String,
    val uuid: String?,
    val type: SenderType,
    val hasPermission: (String) -> Boolean,
    val notify: (String) -> Unit = {},
) {
    fun canUse(permission: String): Boolean = type == SenderType.CONSOLE || hasPermission(permission)
}

enum class SenderType {
    PLAYER,
    CONSOLE,
}
