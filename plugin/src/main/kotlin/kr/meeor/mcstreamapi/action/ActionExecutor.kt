package kr.meeor.mcstreamapi.action

import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.placeholder.PlaceholderResolution
import kr.meeor.mcstreamapi.placeholder.PlaceholderResolver

class ActionExecutor(
    private val platform: ActionPlatform,
    private val logger: PluginLogger? = null,
    private val placeholderResolver: PlaceholderResolver? = null,
) {
    fun execute(context: ActionContext, actions: List<Action>): List<ActionExecutionResult> {
        return platform.runOnMainThread {
            actions.map { action ->
                runCatching { executeOne(context, action) }
                    .getOrElse { throwable ->
                        val actionType = action.typeName()
                        logger?.error(
                            "Action failed rewardId=${context.rewardId} actionType=$actionType",
                            throwable,
                        )
                        ActionExecutionResult.failure(actionType, "EXCEPTION")
                    }
            }
        }
    }

    private fun executeOne(context: ActionContext, action: Action): ActionExecutionResult {
        return when (action) {
            is Action.Command -> executeCommand(context, action)
            is Action.AtPlayerCommand -> executeAtPlayerCommand(context, action)
            is Action.Summon -> executeSummon(context, action)
            is Action.Sound -> executeSound(context, action)
            is Action.Give -> executeGive(context, action)
            is Action.DynamicCustomGive -> executeDynamicCustomGive(context, action)
            is Action.Chat -> executeChat(context, action)
            is Action.Broadcast -> executeBroadcast(context, action)
            is Action.Title -> executeTitle(context, action)
        }
    }

    private fun executeCommand(context: ActionContext, action: Action.Command): ActionExecutionResult {
        val command = render(context, action.command).replace(ActionTarget.SELF_TARGET, context.playerName)
        return dispatchCommand(context, command)
    }

    private fun executeAtPlayerCommand(context: ActionContext, action: Action.AtPlayerCommand): ActionExecutionResult {
        if (!platform.isPlayerOnline(context.playerName)) {
            return ActionExecutionResult.failure("at_player_cmd", "PLAYER_OFFLINE")
        }
        val command = render(context, action.command).replace(ActionTarget.SELF_TARGET, context.playerName)
        return dispatchCommand(context, "execute at ${context.playerName} run $command", "at_player_cmd")
    }

    private fun executeSummon(context: ActionContext, action: Action.Summon): ActionExecutionResult {
        val target = action.target.resolve(context)
        if (!platform.isPlayerOnline(target)) {
            return ActionExecutionResult.failure("summon", "PLAYER_OFFLINE")
        }
        val renderedEntity = renderDetailed(context, action.entity)
        val entity = renderedEntity.value
        val count = renderedEntity.randomAmounts.firstOrNull() ?: 1
        repeat(count) {
            val result = dispatchCommand(context, "execute at $target run summon $entity ~ ~ ~", "summon")
            if (!result.success) {
                return result
            }
        }
        return ActionExecutionResult.success("summon")
    }

    private fun executeSound(context: ActionContext, action: Action.Sound): ActionExecutionResult {
        val target = action.target.resolve(context)
        if (!platform.isPlayerOnline(target)) {
            return ActionExecutionResult.failure("sound", "PLAYER_OFFLINE")
        }
        val sound = render(context, action.sound)
        val source = render(context, action.source)
        return dispatchCommand(
            context = context,
            command = "execute at $target run playsound $sound $source $target ~ ~ ~ ${action.volume} ${action.pitch}",
            actionType = "sound",
        )
    }

    private fun dispatchCommand(
        context: ActionContext,
        command: String,
        actionType: String = "cmd",
    ): ActionExecutionResult {
        val dispatched = runCatching { platform.dispatchConsoleCommand(command) }
            .getOrElse { throwable ->
                logger?.error(
                    "Command action failed rewardId=${context.rewardId} actionType=$actionType command=$command",
                    throwable,
                )
                return ActionExecutionResult.failure(actionType, "COMMAND_EXCEPTION")
            }

        return if (dispatched) {
            ActionExecutionResult.success(actionType)
        } else {
            logger?.warning("Command action returned false rewardId=${context.rewardId} actionType=$actionType command=$command")
            ActionExecutionResult.failure(actionType, "COMMAND_FAILED")
        }
    }

    private fun executeGive(context: ActionContext, action: Action.Give): ActionExecutionResult {
        val target = action.target.resolve(context)
        if (!platform.isPlayerOnline(target)) {
            return ActionExecutionResult.failure("give", "PLAYER_OFFLINE")
        }

        return platform.giveItem(
            playerName = target,
            material = render(context, action.material),
            amount = resolveGiveAmount(context, action),
            name = action.name?.let { render(context, it) },
            lore = action.lore.map { render(context, it) },
            meta = renderMeta(context, action.meta),
        )
    }

    private fun resolveGiveAmount(context: ActionContext, action: Action.Give): Int {
        val customItemKey = action.customItemKey ?: return action.amount.resolve()
        return context.placeholderEventState.cachedCustomItemAmount(customItemKey) {
            action.amount.resolve()
        } ?: action.amount.resolve()
    }

    private fun executeDynamicCustomGive(context: ActionContext, action: Action.DynamicCustomGive): ActionExecutionResult {
        val renderedItemReference = render(context, action.raw["item"]?.toString().orEmpty())
        if (!renderedItemReference.isCustomItemReference()) {
            return ActionExecutionResult.failure("custom-give", "INVALID_CUSTOM_ITEM_REFERENCE")
        }
        val renderedItem = renderedItemReference.toCustomItemName()
        val raw = action.raw.toMutableMap()
        raw["item"] = renderedItem

        val parsed = ActionParser(action.customItems).parse(listOf(raw))
        val disabled = parsed.disabledActions.firstOrNull()
        if (disabled != null) {
            return ActionExecutionResult.failure("custom-give", disabled.reason)
        }

        val give = parsed.actions.singleOrNull() as? Action.Give
            ?: return ActionExecutionResult.failure("custom-give", "INVALID_ITEM")
        return executeGive(context, give)
    }

    private fun executeChat(context: ActionContext, action: Action.Chat): ActionExecutionResult {
        if (!platform.isPlayerOnline(context.playerName)) {
            return ActionExecutionResult.failure("chat", "PLAYER_OFFLINE")
        }

        return if (platform.sendPrivateMessage(context.playerName, render(context, action.message))) {
            ActionExecutionResult.success("chat")
        } else {
            ActionExecutionResult.failure("chat", "MESSAGE_FAILED")
        }
    }

    private fun executeBroadcast(context: ActionContext, action: Action.Broadcast): ActionExecutionResult {
        platform.broadcast(render(context, action.message))
        return ActionExecutionResult.success("broadcast")
    }

    private fun executeTitle(context: ActionContext, action: Action.Title): ActionExecutionResult {
        val target = action.target.resolve(context)
        if (!platform.isPlayerOnline(target)) {
            return ActionExecutionResult.failure("title", "PLAYER_OFFLINE")
        }

        platform.sendTitle(
            playerName = target,
            title = render(context, action.title),
            subtitle = action.subtitle?.let { render(context, it) },
            fadeInTicks = action.fadeInTicks,
            stayTicks = action.stayTicks,
            fadeOutTicks = action.fadeOutTicks,
        )
        return ActionExecutionResult.success("title")
    }

    private fun render(context: ActionContext, value: String): String {
        return renderDetailed(context, value).value
    }

    private fun renderDetailed(context: ActionContext, value: String): PlaceholderResolution {
        val placeholderContext = context.placeholderContext ?: return PlaceholderResolution(value, emptyList())
        return placeholderResolver?.resolveDetailed(
            template = value,
            context = placeholderContext,
            eventState = context.placeholderEventState,
            customItems = context.customItems,
        ) ?: PlaceholderResolution(value, emptyList())
    }

    private fun renderMeta(context: ActionContext, meta: GiveItemMeta): GiveItemMeta {
        return meta.copy(
            enchantments = meta.enchantments.map { it.copy(enchantment = render(context, it.enchantment)) },
            persistentData = meta.persistentData.map { entry ->
                entry.copy(
                    key = render(context, entry.key),
                    value = if (entry.value is String) render(context, entry.value) else entry.value,
                )
            },
            playerHead = meta.playerHead?.let { render(context, it) },
            customPotionEffects = meta.customPotionEffects.map { it.copy(effect = render(context, it.effect)) },
            book = meta.book?.copy(
                title = meta.book.title?.let { render(context, it) },
                author = meta.book.author?.let { render(context, it) },
                pages = meta.book.pages.map { render(context, it) },
            ),
            attributes = meta.attributes.map {
                it.copy(
                    attribute = render(context, it.attribute),
                    operation = render(context, it.operation),
                    slot = it.slot?.let { slot -> render(context, slot) },
                    name = it.name?.let { name -> render(context, name) },
                )
            },
        )
    }

    private fun Action.typeName(): String {
        return when (this) {
            is Action.Command -> "cmd"
            is Action.AtPlayerCommand -> "at_player_cmd"
            is Action.Summon -> "summon"
            is Action.Sound -> "sound"
            is Action.Give -> "give"
            is Action.DynamicCustomGive -> "custom-give"
            is Action.Chat -> "chat"
            is Action.Broadcast -> "broadcast"
            is Action.Title -> "title"
        }
    }

    private fun String.toCustomItemName(): String {
        val trimmed = trim()
        return when {
            trimmed.startsWith("{item.") && trimmed.endsWith("}") ->
                trimmed.removePrefix("{item.").removeSuffix("}").trim()
            else -> trimmed
        }
    }

    private fun String.isCustomItemReference(): Boolean {
        val trimmed = trim()
        if (!trimmed.startsWith("{item.") || !trimmed.endsWith("}")) {
            return false
        }
        val key = trimmed.removePrefix("{item.").removeSuffix("}").trim()
        return key.isNotBlank() && !key.endsWith(".name") && !key.endsWith(".amount")
    }
}
