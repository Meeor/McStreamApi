package kr.meeor.mcstreamapi.command

data class McaCommandResult(
    val message: String,
    val handled: Boolean = true,
    val clickUrl: String? = null,
    val consoleLog: String? = null,
)
