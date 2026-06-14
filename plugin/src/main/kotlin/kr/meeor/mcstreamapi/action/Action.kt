package kr.meeor.mcstreamapi.action

sealed class Action {
    data class Command(val command: String) : Action()

    data class AtPlayerCommand(val command: String) : Action()

    data class Summon(
        val target: ActionTarget,
        val entity: String,
    ) : Action()

    data class Sound(
        val target: ActionTarget,
        val sound: String,
        val source: String,
        val volume: Double,
        val pitch: Double,
    ) : Action()

    data class Give(
        val target: ActionTarget,
        val material: String,
        val amount: ActionQuantity,
        val name: String?,
        val lore: List<String>,
        val meta: GiveItemMeta = GiveItemMeta(),
        val customItemKey: String? = null,
    ) : Action()

    data class DynamicCustomGive(
        val raw: Map<String, Any?>,
        val customItems: Map<String, Map<String, Any?>>,
    ) : Action()

    data class Chat(val message: String) : Action()

    data class Broadcast(val message: String) : Action()

    data class Title(
        val target: ActionTarget,
        val title: String,
        val subtitle: String?,
        val fadeInTicks: Int,
        val stayTicks: Int,
        val fadeOutTicks: Int,
    ) : Action()
}

data class ActionTarget(
    val value: String,
) {
    fun resolve(context: ActionContext): String {
        return if (value == SELF_TARGET) {
            context.playerName
        } else {
            value
        }
    }

    companion object {
        const val SELF_TARGET = "@s"
    }
}
