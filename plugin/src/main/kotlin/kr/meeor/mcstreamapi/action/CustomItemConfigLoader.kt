package kr.meeor.mcstreamapi.action

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

class CustomItemConfigLoader {
    fun load(path: Path): CustomItemConfig {
        if (!Files.exists(path)) {
            return CustomItemConfig(emptyMap())
        }

        val raw = Files.newInputStream(path).use { input ->
            Yaml().load<Map<String, Any?>>(input) ?: emptyMap()
        }
        val itemsRoot = raw["items"] as? Map<*, *> ?: return CustomItemConfig(emptyMap())

        return CustomItemConfig(
            items = itemsRoot.mapNotNull { (name, value) ->
                val itemName = name?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val itemConfig = value as? Map<String, Any?> ?: return@mapNotNull null
                itemName to itemConfig
            }.toMap(),
        )
    }
}

data class CustomItemConfig(
    val items: Map<String, Map<String, Any?>>,
)
