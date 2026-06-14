package kr.meeor.mcstreamapi.placeholder

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

class RandomConfigLoader {
    fun load(path: Path): RandomTable {
        if (!Files.exists(path)) {
            return RandomTable(emptyMap())
        }

        val raw = Files.newInputStream(path).use { input ->
            Yaml().load<Map<String, Any?>>(input) ?: emptyMap()
        }

        return RandomTable(
            entries = raw.mapValues { (_, value) -> parseEntries(value) },
        )
    }

    private fun parseEntries(value: Any?): List<RandomEntry> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            when (item) {
                is String -> RandomEntry(value = item, chance = RandomEntry.DEFAULT_CHANCE)
                is Map<*, *> -> {
                    val entryValue = item["value"]?.toString() ?: return@mapNotNull null
                    val chance = when (val rawChance = item["chance"]) {
                        is Number -> rawChance.toInt()
                        is String -> rawChance.toIntOrNull() ?: RandomEntry.DEFAULT_CHANCE
                        else -> RandomEntry.DEFAULT_CHANCE
                    }
                    RandomEntry(
                        value = entryValue,
                        chance = chance,
                        amount = RandomAmount.parse(item["amount"]),
                        display = item["display"]?.toString(),
                    )
                }
                else -> null
            }
        }
    }
}
