package kr.meeor.mcstreamapi.reward

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

class ApiRewardConfigLoader {
    fun load(path: Path): ApiRewardConfig {
        if (!Files.exists(path)) {
            return ApiRewardConfig(emptyMap())
        }

        val raw = Files.newInputStream(path).use { input ->
            Yaml().load<Map<String, Any?>>(input) ?: emptyMap()
        }
        val rewardsRoot = raw["rewards"] as? Map<*, *> ?: return ApiRewardConfig(emptyMap())

        return ApiRewardConfig(
            rewardsByPlatform = rewardsRoot.mapValues { (_, value) ->
                val rawRewards = value as? List<*> ?: return@mapValues emptyList()
                rawRewards.mapNotNull { reward ->
                    @Suppress("UNCHECKED_CAST")
                    reward as? Map<String, Any?>
                }
            }.mapKeys { (platform, _) -> platform.toString().lowercase() },
        )
    }
}

data class ApiRewardConfig(
    val rewardsByPlatform: Map<String, List<Map<String, Any?>>>,
) {
    fun amountSuggestions(): List<String> {
        return rewardsByPlatform.values
            .flatten()
            .mapNotNull { it["amount"]?.toString()?.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { it.toExactAmountSuggestion() }
            .distinct()
            .sortedBy { it.toLongOrNull() ?: Long.MAX_VALUE }
    }

    private fun String.toExactAmountSuggestion(): String? {
        return when (val rule = AmountRule.parse(this).getOrNull()) {
            is AmountRule.Exact -> rule.amount.toString()
            is AmountRule.Range,
            is AmountRule.Plus,
            null -> null
        }
    }
}
