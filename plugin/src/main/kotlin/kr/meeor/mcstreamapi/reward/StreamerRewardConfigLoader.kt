package kr.meeor.mcstreamapi.reward

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

class StreamerRewardConfigLoader {
    fun load(path: Path): StreamerRewardConfig {
        if (!Files.exists(path)) {
            return StreamerRewardConfig(emptyMap())
        }

        val raw = Files.newInputStream(path).use { input ->
            Yaml().load<Map<String, Any?>>(input) ?: emptyMap()
        }
        val streamersRoot = raw["streamers"] as? Map<*, *> ?: return StreamerRewardConfig(emptyMap())
        val rewardsByPlayerUuid = streamersRoot.mapNotNull { (rawUuid, rawPlatforms) ->
            val playerUuid = rawUuid?.toString()?.trim()?.lowercase().orEmpty()
            val platforms = rawPlatforms as? Map<*, *> ?: return@mapNotNull null
            if (playerUuid.isBlank()) {
                return@mapNotNull null
            }
            playerUuid to platforms.mapNotNull { (rawPlatform, rawRewards) ->
                val platform = rawPlatform?.toString()?.trim()?.lowercase().orEmpty()
                if (platform.isBlank()) {
                    return@mapNotNull null
                }
                val rewards = (rawRewards as? List<*>).orEmpty().mapNotNull { reward ->
                    @Suppress("UNCHECKED_CAST")
                    reward as? Map<String, Any?>
                }
                platform to rewards
            }.toMap()
        }.toMap()

        return StreamerRewardConfig(rewardsByPlayerUuid)
    }
}

data class StreamerRewardConfig(
    val rewardsByPlayerUuid: Map<String, Map<String, List<Map<String, Any?>>>>,
) {
    fun rewards(playerUuid: String, platform: String): List<Map<String, Any?>> {
        return rewardsByPlayerUuid[playerUuid.trim().lowercase()]
            ?.get(platform.trim().lowercase())
            .orEmpty()
    }

    fun platforms(playerUuid: String): Set<String> {
        return rewardsByPlayerUuid[playerUuid.trim().lowercase()]?.keys.orEmpty()
    }
}
