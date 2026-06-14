package kr.meeor.mcstreamapi.action

interface ActionPlatform {
    fun runOnMainThread(block: () -> List<ActionExecutionResult>): List<ActionExecutionResult>

    fun dispatchConsoleCommand(command: String): Boolean

    fun isPlayerOnline(playerName: String): Boolean

    fun giveItem(
        playerName: String,
        material: String,
        amount: Int,
        name: String?,
        lore: List<String>,
        meta: GiveItemMeta,
    ): ActionExecutionResult

    fun sendPrivateMessage(playerName: String, message: String): Boolean

    fun broadcast(message: String)

    fun sendTitle(
        playerName: String,
        title: String,
        subtitle: String?,
        fadeInTicks: Int,
        stayTicks: Int,
        fadeOutTicks: Int,
    )
}
