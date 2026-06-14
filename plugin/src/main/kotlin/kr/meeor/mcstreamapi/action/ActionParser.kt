package kr.meeor.mcstreamapi.action

class ActionParser(
    private val customItems: Map<String, Map<String, Any?>> = emptyMap(),
) {
    fun parse(rawActions: List<Map<String, Any?>>): ActionParseResult {
        val actions = mutableListOf<Action>()
        val disabled = mutableListOf<DisabledAction>()

        rawActions.forEachIndexed { index, raw ->
            when (raw.string("type")?.lowercase()) {
                "cmd" -> parseCommand(raw, index, actions, disabled)
                "at_player_cmd" -> parseAtPlayerCommand(raw, index, actions, disabled)
                "summon" -> parseSummon(raw, index, actions, disabled)
                "sound" -> parseSound(raw, index, actions, disabled)
                "give" -> parseGive(raw, index, actions, disabled, includeMeta = false)
                "custom-give" -> parseCustomGive(raw, index, actions, disabled)
                "chat" -> parseChat(raw, index, actions, disabled)
                "broadcast" -> parseBroadcast(raw, index, actions, disabled)
                "title" -> parseTitle(raw, index, actions, disabled)
                null -> disabled.add(DisabledAction(index, "unknown", "MISSING_TYPE"))
                else -> disabled.add(DisabledAction(index, raw.string("type").orEmpty(), "UNKNOWN_TYPE"))
            }
        }

        return ActionParseResult(actions = actions, disabledActions = disabled)
    }

    private fun parseCommand(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val command = raw.requiredString("command") ?: run {
            disabled.add(DisabledAction(index, "cmd", "MISSING_COMMAND"))
            return
        }

        actions.add(Action.Command(command))
    }

    private fun parseAtPlayerCommand(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val command = raw.requiredString("command") ?: run {
            disabled.add(DisabledAction(index, "at_player_cmd", "MISSING_COMMAND"))
            return
        }

        actions.add(Action.AtPlayerCommand(command))
    }

    private fun parseSummon(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val entity = raw.requiredString("entity") ?: run {
            disabled.add(DisabledAction(index, "summon", "MISSING_ENTITY"))
            return
        }

        actions.add(
            Action.Summon(
                target = ActionTarget(raw.string("target") ?: ActionTarget.SELF_TARGET),
                entity = entity,
            ),
        )
    }

    private fun parseSound(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val sound = raw.requiredString("sound") ?: run {
            disabled.add(DisabledAction(index, "sound", "MISSING_SOUND"))
            return
        }
        val volume = raw.double("volume") ?: 1.0
        val pitch = raw.double("pitch") ?: 1.0
        if (volume < 0.0) {
            disabled.add(DisabledAction(index, "sound", "INVALID_VOLUME"))
            return
        }
        if (pitch < 0.0) {
            disabled.add(DisabledAction(index, "sound", "INVALID_PITCH"))
            return
        }

        actions.add(
            Action.Sound(
                target = ActionTarget(raw.string("target") ?: ActionTarget.SELF_TARGET),
                sound = sound,
                source = raw.string("source") ?: "master",
                volume = volume,
                pitch = pitch,
            ),
        )
    }

    private fun parseGive(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
        includeMeta: Boolean,
        customItemKey: String? = null,
    ) {
        val material = raw.requiredString("material") ?: run {
            disabled.add(DisabledAction(index, "give", "MISSING_MATERIAL"))
            return
        }
        val amount = ActionQuantity.parse(raw["amount"], default = 1).getOrElse {
            disabled.add(DisabledAction(index, "give", "INVALID_AMOUNT"))
            return
        }

        actions.add(
            Action.Give(
                target = ActionTarget(raw.string("target") ?: ActionTarget.SELF_TARGET),
                material = material,
                amount = amount,
                name = raw.string("name"),
                lore = raw.stringList("lore"),
                meta = if (includeMeta) parseGiveMeta(raw) else GiveItemMeta(),
                customItemKey = customItemKey,
            ),
        )
    }

    private fun parseCustomGive(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val itemName = raw.requiredString("item")?.toCustomItemName() ?: run {
            disabled.add(DisabledAction(index, "custom-give", "MISSING_ITEM"))
            return
        }
        if (itemName.containsPlaceholder()) {
            actions.add(Action.DynamicCustomGive(raw = raw, customItems = customItems))
            return
        }
        val item = customItems[itemName] ?: run {
            disabled.add(DisabledAction(index, "custom-give", "UNKNOWN_ITEM"))
            return
        }
        val merged = item.toMutableMap()
        raw.forEach { (key, value) ->
            if (key != "type" && key != "item") {
                merged[key] = value
            }
        }
        parseGive(
            raw = merged,
            index = index,
            actions = actions,
            disabled = disabled,
            includeMeta = true,
            customItemKey = itemName.takeUnless { raw.containsKey("amount") },
        )
    }

    private fun parseGiveMeta(raw: Map<String, Any?>): GiveItemMeta {
        return GiveItemMeta(
            customModelData = raw.int("customModelData"),
            unbreakable = raw.boolean("unbreakable"),
            glow = raw.boolean("glow") ?: raw.boolean("glint"),
            itemFlags = raw.stringList("itemFlags") + raw.stringList("item-flags"),
            enchantments = parseEnchantments(raw["enchantments"]),
            persistentData = parsePersistentData(raw["persistentData"]) + parsePersistentData(raw["pdc"]) +
                parsePersistentData(raw["itemTag"]),
            playerHead = raw.string("playerHead") ?: raw.string("skullOwner") ?: raw.string("playerName"),
            customPotionEffects = parsePotionEffects(raw["customPotionEffects"]) + parsePotionEffects(raw["potionEffects"]),
            book = parseBook(raw),
            attributes = parseAttributes(raw["attributes"]),
        )
    }

    private fun parseEnchantments(value: Any?): List<GiveEnchantment> {
        return when (value) {
            is Map<*, *> -> value.mapNotNull { (key, level) ->
                val enchantment = key?.toString()?.trim().orEmpty()
                val parsedLevel = level.toIntOrNull() ?: return@mapNotNull null
                GiveEnchantment(enchantment, parsedLevel)
            }
            is List<*> -> value.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val enchantment = map.stringAny("enchantment") ?: map.stringAny("type") ?: map.stringAny("name")
                    ?: return@mapNotNull null
                GiveEnchantment(
                    enchantment = enchantment,
                    level = map.intAny("level") ?: 1,
                    ignoreLevelRestriction = map.booleanAny("ignoreLevelRestriction") ?: true,
                )
            }
            else -> emptyList()
        }
    }

    private fun parsePersistentData(value: Any?): List<PersistentDataEntry> {
        return when (value) {
            is Map<*, *> -> value.mapNotNull { (key, rawValue) ->
                val rawKey = key?.toString()?.trim().orEmpty()
                when (rawValue) {
                    is Map<*, *> -> persistentDataEntry(rawKey, rawValue)
                    else -> persistentDataEntry(rawKey, rawValue)
                }
            }
            is List<*> -> value.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val key = map.stringAny("key") ?: return@mapNotNull null
                persistentDataEntry(key, map)
            }
            else -> emptyList()
        }
    }

    private fun persistentDataEntry(key: String, value: Any?): PersistentDataEntry? {
        if (key.isBlank()) {
            return null
        }
        val type = when (value) {
            is Map<*, *> -> value.stringAny("type")?.uppercase()
            is Int -> "INT"
            is Long -> "LONG"
            is Float, is Double -> "DOUBLE"
            is Boolean -> "BOOLEAN"
            else -> "STRING"
        }?.let { runCatching { PersistentDataType.valueOf(it) }.getOrNull() } ?: return null
        val rawValue = if (value is Map<*, *>) value["value"] else value
        val parsedValue = when (type) {
            PersistentDataType.STRING -> rawValue?.toString() ?: return null
            PersistentDataType.INT -> rawValue.toIntOrNull() ?: return null
            PersistentDataType.LONG -> rawValue.toLongOrNull() ?: return null
            PersistentDataType.DOUBLE -> rawValue.toDoubleOrNull() ?: return null
            PersistentDataType.BOOLEAN -> rawValue.toBooleanOrNull() ?: return null
        }
        return PersistentDataEntry(key = key, type = type, value = parsedValue)
    }

    private fun parsePotionEffects(value: Any?): List<GivePotionEffect> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val effect = map.stringAny("effect") ?: map.stringAny("type") ?: return@mapNotNull null
            GivePotionEffect(
                effect = effect,
                durationTicks = map.intAny("durationTicks") ?: map.intAny("duration") ?: 200,
                amplifier = map.intAny("amplifier") ?: 0,
                ambient = map.booleanAny("ambient") ?: false,
                particles = map.booleanAny("particles") ?: true,
                icon = map.booleanAny("icon") ?: true,
            )
        }
    }

    private fun parseBook(raw: Map<String, Any?>): GiveBook? {
        val bookMap = raw["book"] as? Map<*, *>
        val title = bookMap?.stringAny("title") ?: raw.string("bookTitle") ?: raw.string("title")
        val author = bookMap?.stringAny("author") ?: raw.string("bookAuthor") ?: raw.string("author")
        val pages = bookMap?.stringListAny("pages") ?: raw.stringList("pages")
        return if (title == null && author == null && pages.isEmpty()) {
            null
        } else {
            GiveBook(title = title, author = author, pages = pages)
        }
    }

    private fun parseAttributes(value: Any?): List<GiveAttribute> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val attribute = map.stringAny("attribute") ?: map.stringAny("type") ?: return@mapNotNull null
            val amount = map.doubleAny("amount") ?: return@mapNotNull null
            GiveAttribute(
                attribute = attribute,
                amount = amount,
                operation = map.stringAny("operation") ?: "ADD_NUMBER",
                slot = map.stringAny("slot"),
                name = map.stringAny("name"),
            )
        }
    }

    private fun parseChat(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val message = raw.requiredString("message") ?: run {
            disabled.add(DisabledAction(index, "chat", "MISSING_MESSAGE"))
            return
        }

        actions.add(Action.Chat(message))
    }

    private fun parseBroadcast(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val message = raw.requiredString("message") ?: run {
            disabled.add(DisabledAction(index, "broadcast", "MISSING_MESSAGE"))
            return
        }

        actions.add(Action.Broadcast(message))
    }

    private fun parseTitle(
        raw: Map<String, Any?>,
        index: Int,
        actions: MutableList<Action>,
        disabled: MutableList<DisabledAction>,
    ) {
        val title = raw.requiredString("title") ?: run {
            disabled.add(DisabledAction(index, "title", "MISSING_TITLE"))
            return
        }

        actions.add(
            Action.Title(
                target = ActionTarget(raw.string("target") ?: ActionTarget.SELF_TARGET),
                title = title,
                subtitle = raw.string("subtitle"),
                fadeInTicks = raw.int("fadeInTicks") ?: 10,
                stayTicks = raw.int("stayTicks") ?: 70,
                fadeOutTicks = raw.int("fadeOutTicks") ?: 20,
            ),
        )
    }

    private fun Map<String, Any?>.requiredString(key: String): String? {
        return string(key)?.takeIf { it.isNotBlank() }
    }

    private fun String.toCustomItemName(): String {
        val trimmed = trim()
        return when {
            trimmed.startsWith("{item.") && trimmed.endsWith("}") ->
                trimmed.removePrefix("{item.").removeSuffix("}").trim()
            else -> trimmed
        }
    }

    private fun String.containsPlaceholder(): Boolean = PLACEHOLDER_PATTERN.containsMatchIn(this)

    private fun Map<String, Any?>.string(key: String): String? = this[key]?.toString()?.trim()

    private fun Map<String, Any?>.int(key: String): Int? {
        return when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun Map<String, Any?>.double(key: String): Double? {
        return when (val value = this[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun Map<String, Any?>.boolean(key: String): Boolean? = this[key].toBooleanOrNull()

    private fun Map<String, Any?>.stringList(key: String): List<String> {
        val value = this[key] as? List<*> ?: return emptyList()
        return value.mapNotNull { it?.toString() }
    }

    private fun Map<*, *>.stringAny(key: String): String? = this[key]?.toString()?.trim()

    private fun Map<*, *>.intAny(key: String): Int? = this[key].toIntOrNull()

    private fun Map<*, *>.doubleAny(key: String): Double? = this[key].toDoubleOrNull()

    private fun Map<*, *>.booleanAny(key: String): Boolean? = this[key].toBooleanOrNull()

    private fun Map<*, *>.stringListAny(key: String): List<String> {
        val value = this[key] as? List<*> ?: return emptyList()
        return value.mapNotNull { it?.toString() }
    }

    private fun Any?.toIntOrNull(): Int? {
        return when (this) {
            is Number -> toInt()
            is String -> toIntOrNull()
            else -> null
        }
    }

    private fun Any?.toLongOrNull(): Long? {
        return when (this) {
            is Number -> toLong()
            is String -> toLongOrNull()
            else -> null
        }
    }

    private fun Any?.toDoubleOrNull(): Double? {
        return when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull()
            else -> null
        }
    }

    private fun Any?.toBooleanOrNull(): Boolean? {
        return when (this) {
            is Boolean -> this
            is String -> when (lowercase()) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex("""\{[a-zA-Z0-9_.-]+}""")
    }
}

data class ActionParseResult(
    val actions: List<Action>,
    val disabledActions: List<DisabledAction>,
)

data class DisabledAction(
    val index: Int,
    val type: String,
    val reason: String,
)
