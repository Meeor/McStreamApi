package kr.meeor.mcstreamapi.placeholder

import kr.meeor.mcstreamapi.action.ActionQuantity

class PlaceholderResolver(
    private val randomResolver: RandomResolver = RandomResolver(RandomTable(emptyMap())),
) {
    fun resolve(
        template: String,
        context: PlaceholderContext,
        eventState: PlaceholderEventState = PlaceholderEventState(),
        customItems: Map<String, Map<String, Any?>> = emptyMap(),
        gameValueRenderer: (String) -> String = { it },
    ): String {
        return resolveDetailed(template, context, eventState, customItems, gameValueRenderer).value
    }

    fun resolveDetailed(
        template: String,
        context: PlaceholderContext,
        eventState: PlaceholderEventState = PlaceholderEventState(),
        customItems: Map<String, Map<String, Any?>> = emptyMap(),
        gameValueRenderer: (String) -> String = { it },
    ): PlaceholderResolution {
        val randomAmounts = mutableListOf<Int>()
        val value = PLACEHOLDER_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            resolveToken(key, context, eventState, randomAmounts, customItems, gameValueRenderer) ?: match.value
        }
        return PlaceholderResolution(value = value, randomAmounts = randomAmounts)
    }

    private fun resolveToken(
        key: String,
        context: PlaceholderContext,
        eventState: PlaceholderEventState,
        randomAmounts: MutableList<Int>,
        customItems: Map<String, Map<String, Any?>>,
        gameValueRenderer: (String) -> String,
    ): String? {
        return when {
            key.startsWith("random_once.") -> {
                val reference = RandomReference.parse(key.removePrefix("random_once."))
                val selection = randomResolver.resolveEntry(reference.key)?.toSelection() ?: return null
                resolveRandomProperty(reference, selection, eventState, randomAmounts, customItems, gameValueRenderer)
            }
            key.startsWith("random.") -> {
                val reference = RandomReference.parse(key.removePrefix("random."))
                val selection = eventState.cachedRandomSelection(reference.key) {
                    randomResolver.resolveEntry(reference.key)
                } ?: return null
                resolveRandomProperty(reference, selection, eventState, randomAmounts, customItems, gameValueRenderer)
            }
            key.startsWith("item.") -> resolveItemToken(
                key.removePrefix("item."),
                eventState,
                customItems,
                gameValueRenderer,
            )
            else -> standardValue(key, context)
        }
    }

    private fun resolveRandomProperty(
        reference: RandomReference,
        selection: RandomSelection,
        eventState: PlaceholderEventState,
        randomAmounts: MutableList<Int>,
        customItems: Map<String, Map<String, Any?>>,
        gameValueRenderer: (String) -> String,
    ): String? {
        return when (reference.property) {
            RandomProperty.VALUE -> {
                selection.resolvedAmount?.let(randomAmounts::add)
                if (selection.value.toCustomItemKey() == null) gameValueRenderer(selection.value) else selection.value
            }
            RandomProperty.DISPLAY -> selection.display ?: gameValueRenderer(selection.value)
            RandomProperty.AMOUNT -> (selection.resolvedAmount ?: 1).toString()
            RandomProperty.ITEM_NAME -> {
                val itemKey = selection.value.toCustomItemKey() ?: return null
                val item = customItems[itemKey]
                item?.get("name")?.toString()
                    ?: item?.get("material")?.toString()?.let(gameValueRenderer)
                    ?: itemKey
            }
            RandomProperty.ITEM_AMOUNT -> {
                val itemKey = selection.value.toCustomItemKey() ?: return null
                val amount = eventState.cachedCustomItemAmount(itemKey) {
                    ActionQuantity.parse(customItems[itemKey]?.get("amount"), default = 1).getOrNull()?.resolve()
                } ?: 1
                amount.toString()
            }
        }
    }

    private fun resolveItemToken(
        reference: String,
        eventState: PlaceholderEventState,
        customItems: Map<String, Map<String, Any?>>,
        gameValueRenderer: (String) -> String,
    ): String? {
        val itemReference = ItemReference.parse(reference) ?: return null
        val item = customItems[itemReference.key] ?: return null
        return when (itemReference.property) {
            ItemProperty.NAME -> item["name"]?.toString()
                ?: item["material"]?.toString()?.let(gameValueRenderer)
                ?: itemReference.key
            ItemProperty.AMOUNT -> {
                val amount = eventState.cachedCustomItemAmount(itemReference.key) {
                    ActionQuantity.parse(item["amount"], default = 1).getOrNull()?.resolve()
                } ?: 1
                amount.toString()
            }
        }
    }

    private fun standardValue(key: String, context: PlaceholderContext): String? {
        return when (key) {
            "player", "player_name" -> context.playerName
            "uuid", "player_uuid" -> context.playerUuid
            "streamer", "streamer_name" -> context.streamerName
            "platform", "streamer_platform" -> context.platform
            "donator", "donator_name" -> context.donatorName
            "amount", "donation_amount" -> context.amount.toString()
            "unit_count" -> context.unitCount.toString()
            "message", "donation_message" -> context.message.orEmpty()
            "reward", "reward_id" -> context.rewardId
            else -> null
        }
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex("""\{([a-zA-Z0-9_.-]+)}""")
    }
}

data class PlaceholderResolution(
    val value: String,
    val randomAmounts: List<Int>,
)

private data class RandomReference(
    val key: String,
    val property: RandomProperty,
) {
    companion object {
        fun parse(value: String): RandomReference {
            return when {
                value.endsWith(".item.name") -> RandomReference(value.removeSuffix(".item.name"), RandomProperty.ITEM_NAME)
                value.endsWith(".item.amount") -> RandomReference(value.removeSuffix(".item.amount"), RandomProperty.ITEM_AMOUNT)
                value.endsWith(".display") -> RandomReference(value.removeSuffix(".display"), RandomProperty.DISPLAY)
                value.endsWith(".amount") -> RandomReference(value.removeSuffix(".amount"), RandomProperty.AMOUNT)
                value.endsWith(".value") -> RandomReference(value.removeSuffix(".value"), RandomProperty.VALUE)
                else -> RandomReference(value, RandomProperty.VALUE)
            }
        }
    }
}

private enum class RandomProperty {
    VALUE,
    DISPLAY,
    AMOUNT,
    ITEM_NAME,
    ITEM_AMOUNT,
}

private fun String.toCustomItemKey(): String? {
    val trimmed = trim()
    return if (trimmed.startsWith("{item.") && trimmed.endsWith("}")) {
        val key = trimmed.removePrefix("{item.").removeSuffix("}").trim()
        key.takeIf { it.isNotBlank() && !it.endsWith(".name") && !it.endsWith(".amount") }
    } else {
        null
    }
}

private data class ItemReference(
    val key: String,
    val property: ItemProperty,
) {
    companion object {
        fun parse(value: String): ItemReference? {
            return when {
                value.endsWith(".name") -> ItemReference(value.removeSuffix(".name"), ItemProperty.NAME)
                value.endsWith(".amount") -> ItemReference(value.removeSuffix(".amount"), ItemProperty.AMOUNT)
                else -> null
            }
        }
    }
}

private enum class ItemProperty {
    NAME,
    AMOUNT,
}
