package kr.meeor.mcstreamapi.action

data class GiveItemMeta(
    val customModelData: Int? = null,
    val unbreakable: Boolean? = null,
    val glow: Boolean? = null,
    val itemFlags: List<String> = emptyList(),
    val enchantments: List<GiveEnchantment> = emptyList(),
    val persistentData: List<PersistentDataEntry> = emptyList(),
    val playerHead: String? = null,
    val customPotionEffects: List<GivePotionEffect> = emptyList(),
    val book: GiveBook? = null,
    val attributes: List<GiveAttribute> = emptyList(),
)

data class GiveEnchantment(
    val enchantment: String,
    val level: Int,
    val ignoreLevelRestriction: Boolean = true,
)

data class PersistentDataEntry(
    val key: String,
    val type: PersistentDataType,
    val value: Any,
)

enum class PersistentDataType {
    STRING,
    INT,
    LONG,
    DOUBLE,
    BOOLEAN,
}

data class GivePotionEffect(
    val effect: String,
    val durationTicks: Int,
    val amplifier: Int,
    val ambient: Boolean = false,
    val particles: Boolean = true,
    val icon: Boolean = true,
)

data class GiveBook(
    val title: String? = null,
    val author: String? = null,
    val pages: List<String> = emptyList(),
)

data class GiveAttribute(
    val attribute: String,
    val amount: Double,
    val operation: String,
    val slot: String? = null,
    val name: String? = null,
)
