package kr.meeor.mcstreamapi.action

import kr.meeor.mcstreamapi.logging.PluginLogger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import java.time.Duration

class BukkitActionPlatform(
    private val plugin: JavaPlugin,
    private val logger: PluginLogger? = null,
) : ActionPlatform {
    override fun runOnMainThread(block: () -> List<ActionExecutionResult>): List<ActionExecutionResult> {
        if (Bukkit.isPrimaryThread()) {
            return block()
        }

        Bukkit.getScheduler().runTask(plugin, Runnable { block() })
        return listOf(ActionExecutionResult.success("scheduled"))
    }

    override fun dispatchConsoleCommand(command: String): Boolean {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
    }

    override fun isPlayerOnline(playerName: String): Boolean {
        return Bukkit.getPlayerExact(playerName)?.isOnline == true
    }

    override fun localizeGameValue(value: String): String = GameTranslationToken.encode(value)

    @Suppress("DEPRECATION")
    override fun giveItem(
        playerName: String,
        material: String,
        amount: Int,
        name: String?,
        lore: List<String>,
        meta: GiveItemMeta,
    ): ActionExecutionResult {
        val player = Bukkit.getPlayerExact(playerName)
            ?: return ActionExecutionResult.failure("give", "PLAYER_OFFLINE")
        val parsedMaterial = Material.matchMaterial(material)
            ?: return ActionExecutionResult.failure("give", "INVALID_MATERIAL")

        val item = ItemStack(parsedMaterial, amount)
        val itemMeta = item.itemMeta
        if (itemMeta != null) {
            if (name != null) {
                itemMeta.setDisplayName(colorize(name))
            }
            if (lore.isNotEmpty()) {
                itemMeta.lore = lore.map(::colorize)
            }
            applyMeta(meta, itemMeta)
            item.itemMeta = itemMeta
        }

        player.inventory.addItem(item)
        return ActionExecutionResult.success("give")
    }

    @Suppress("DEPRECATION")
    private fun applyMeta(spec: GiveItemMeta, meta: ItemMeta) {
        spec.customModelData?.let { meta.setCustomModelData(it) }
        spec.unbreakable?.let { meta.isUnbreakable = it }
        spec.glow?.let { meta.setEnchantmentGlintOverride(it) }

        spec.itemFlags.mapNotNull { flag ->
            runCatching { ItemFlag.valueOf(flag.normalizedEnumName()) }
                .getOrElse {
                    logger?.warning("CUSTOM_ITEM_INVALID_ITEM_FLAG value=$flag")
                    null
                }
        }.takeIf { it.isNotEmpty() }?.let { flags ->
            meta.addItemFlags(*flags.toTypedArray())
        }

        spec.enchantments.forEach { enchantmentSpec ->
            val enchantment = Registry.ENCHANTMENT.lookup(enchantmentSpec.enchantment) ?: run {
                logger?.warning("CUSTOM_ITEM_INVALID_ENCHANTMENT value=${enchantmentSpec.enchantment}")
                return@forEach
            }
            meta.addEnchant(enchantment, enchantmentSpec.level, enchantmentSpec.ignoreLevelRestriction)
        }

        spec.persistentData.forEach { entry ->
            val key = NamespacedKey.fromString(entry.key, plugin) ?: run {
                logger?.warning("CUSTOM_ITEM_INVALID_PDC_KEY key=${entry.key}")
                return@forEach
            }
            when (entry.type) {
                PersistentDataType.STRING -> meta.persistentDataContainer.set(
                    key,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    entry.value.toString(),
                )
                PersistentDataType.INT -> meta.persistentDataContainer.set(
                    key,
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    entry.value as Int,
                )
                PersistentDataType.LONG -> meta.persistentDataContainer.set(
                    key,
                    org.bukkit.persistence.PersistentDataType.LONG,
                    entry.value as Long,
                )
                PersistentDataType.DOUBLE -> meta.persistentDataContainer.set(
                    key,
                    org.bukkit.persistence.PersistentDataType.DOUBLE,
                    entry.value as Double,
                )
                PersistentDataType.BOOLEAN -> meta.persistentDataContainer.set(
                    key,
                    org.bukkit.persistence.PersistentDataType.BYTE,
                    if (entry.value as Boolean) 1.toByte() else 0.toByte(),
                )
            }
        }

        if (meta is SkullMeta) {
            spec.playerHead?.takeIf { it.isNotBlank() }?.let { playerName ->
                meta.owningPlayer = Bukkit.getOfflinePlayer(playerName)
            }
        }

        if (meta is PotionMeta) {
            spec.customPotionEffects.forEach { effectSpec ->
                val effectType = Registry.MOB_EFFECT.lookup(effectSpec.effect) ?: run {
                    logger?.warning("CUSTOM_ITEM_INVALID_POTION_EFFECT value=${effectSpec.effect}")
                    return@forEach
                }
                meta.addCustomEffect(
                    PotionEffect(
                        effectType,
                        effectSpec.durationTicks,
                        effectSpec.amplifier,
                        effectSpec.ambient,
                        effectSpec.particles,
                        effectSpec.icon,
                    ),
                    true,
                )
            }
        }

        if (meta is BookMeta) {
            spec.book?.title?.let { meta.title = colorize(it) }
            spec.book?.author?.let { meta.author = colorize(it) }
            if (spec.book?.pages?.isNotEmpty() == true) {
                meta.pages = spec.book.pages.map(::colorize)
            }
        }

        spec.attributes.forEachIndexed { index, attributeSpec ->
            val attribute = Registry.ATTRIBUTE.lookup(attributeSpec.attribute) ?: run {
                logger?.warning("CUSTOM_ITEM_INVALID_ATTRIBUTE value=${attributeSpec.attribute}")
                return@forEachIndexed
            }
            val operation = runCatching {
                AttributeModifier.Operation.valueOf(attributeSpec.operation.normalizedEnumName())
            }.getOrNull() ?: run {
                logger?.warning(
                    "CUSTOM_ITEM_INVALID_ATTRIBUTE_OPERATION attribute=${attributeSpec.attribute} operation=${attributeSpec.operation}",
                )
                return@forEachIndexed
            }
            val slotGroup = if (attributeSpec.slot == null) {
                EquipmentSlotGroup.ANY
            } else {
                EquipmentSlotGroup.getByName(attributeSpec.slot.lowercase()) ?: run {
                    logger?.warning(
                        "CUSTOM_ITEM_INVALID_ATTRIBUTE_SLOT attribute=${attributeSpec.attribute} slot=${attributeSpec.slot}",
                    )
                    EquipmentSlotGroup.ANY
                }
            }
            val key = NamespacedKey(plugin, "attribute_${index}_${attribute.key.key}".sanitizeKey())
            val modifier = AttributeModifier(
                key,
                attributeSpec.amount,
                operation,
                slotGroup,
            )
            meta.addAttributeModifier(attribute, modifier)
        }
    }

    override fun sendPrivateMessage(playerName: String, message: String): Boolean {
        val player = Bukkit.getPlayerExact(playerName) ?: return false
        player.sendMessage(localizedComponent(message))
        return true
    }

    override fun broadcast(message: String) {
        Bukkit.broadcast(localizedComponent(message))
    }

    override fun sendTitle(
        playerName: String,
        title: String,
        subtitle: String?,
        fadeInTicks: Int,
        stayTicks: Int,
        fadeOutTicks: Int,
    ) {
        val player = Bukkit.getPlayerExact(playerName) ?: return
        player.showTitle(
            Title.title(
                localizedComponent(title),
                subtitle?.let(::localizedComponent) ?: Component.empty(),
                Title.Times.times(
                    fadeInTicks.toDuration(),
                    stayTicks.toDuration(),
                    fadeOutTicks.toDuration(),
                ),
            ),
        )
    }

    private fun colorize(message: String): String {
        return translateColorCodes(message)
    }

    private fun localizedComponent(message: String): Component {
        var component: Component = LEGACY_SERIALIZER.deserialize(colorize(message))
        GameTranslationToken.findAll(message).distinctBy { it.encoded }.forEach { token ->
            val replacement = translationKey(token.value)
                ?.let(Component::translatable)
                ?: Component.text(token.value)
            component = component.replaceText { builder ->
                builder.matchLiteral(token.encoded).replacement(replacement)
            }
        }
        return component
    }

    private fun translationKey(value: String): String? {
        return Registry.MATERIAL.lookup(value)?.translationKey()
            ?: Registry.ENTITY_TYPE.lookup(value)?.translationKey()
            ?: Registry.MOB_EFFECT.lookup(value)?.translationKey()
    }

    private fun Int.toDuration(): Duration = Duration.ofMillis(coerceAtLeast(0) * MILLIS_PER_TICK)

    private fun <T : org.bukkit.Keyed> Registry<T>.lookup(raw: String): T? {
        val normalized = raw.normalizedKey()
        return match(normalized) ?: get(NamespacedKey.fromString(normalized) ?: NamespacedKey.minecraft(normalized))
    }

    private fun String.normalizedEnumName(): String {
        return trim().replace('-', '_').replace(' ', '_').uppercase()
    }

    private fun String.normalizedKey(): String {
        return trim().lowercase().replace(' ', '_')
    }

    private fun String.sanitizeKey(): String {
        return lowercase().replace(Regex("[^a-z0-9/._-]"), "_")
    }

    private companion object {
        val LEGACY_SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
        const val MILLIS_PER_TICK = 50L
    }
}
