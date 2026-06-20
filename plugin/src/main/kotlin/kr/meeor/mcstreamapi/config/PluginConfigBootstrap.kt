package kr.meeor.mcstreamapi.config

import kr.meeor.mcstreamapi.logging.PluginLogger
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

class PluginConfigBootstrap(
    private val dataFolder: Path,
    private val resourceProvider: (String) -> InputStream?,
    private val logger: PluginLogger,
    private val validator: PluginConfigValidator = PluginConfigValidator(),
) {
    fun initialize(): PluginRuntimeState {
        Files.createDirectories(dataFolder)

        val createdFiles = mutableListOf<String>()
        copyDefaultIfMissing("defaults/config.yml", dataFolder.resolve("config.yml"), createdFiles)
        val validation = validator.validate(dataFolder.resolve("config.yml"))
        validation.warnings.forEach { warning -> logger.warning(warning) }

        copyDefaultIfMissing("defaults/Api.yml", dataFolder.resolve("Api.yml"), createdFiles)
        if (validation.streamerRewardsEnabled) {
            copyDefaultIfMissing(
                "defaults/streamer-rewards.yml",
                dataFolder.resolve("streamer-rewards.yml"),
                createdFiles,
                blocksRuntimeOnCreate = false,
            )
        }
        copyDefaultIfMissing("defaults/random.yml", dataFolder.resolve("random.yml"), createdFiles)
        copyDefaultIfMissing("defaults/custom-item.yml", dataFolder.resolve("custom-item.yml"), createdFiles)
        createSecretKeyIfMissing(dataFolder.resolve("secret.key"), createdFiles)
        createDirectoryIfMissing(dataFolder.resolve("tokens"), createdFiles)

        return PluginRuntimeState(
            createdFiles = createdFiles,
            validation = validation,
        )
    }

    private fun copyDefaultIfMissing(
        resourceName: String,
        target: Path,
        createdFiles: MutableList<String>,
        blocksRuntimeOnCreate: Boolean = true,
    ) {
        if (Files.exists(target)) {
            return
        }

        val resource = resourceProvider(resourceName)
        if (resource == null) {
            val fallback = fallbackDefault(resourceName)
                ?: throw IllegalStateException("Missing bundled resource: $resourceName")
            Files.writeString(target, fallback)
            logger.warning("DEFAULT_RESOURCE_FALLBACK resource=$resourceName target=$target")
            if (blocksRuntimeOnCreate) {
                createdFiles.add(dataFolder.relativize(target).toString())
            }
            return
        }

        resource.use { input ->
            Files.copy(input, target)
        }
        if (blocksRuntimeOnCreate) {
            createdFiles.add(dataFolder.relativize(target).toString())
        }
    }

    private fun createSecretKeyIfMissing(target: Path, createdFiles: MutableList<String>) {
        if (Files.exists(target)) {
            return
        }

        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        Files.writeString(target, Base64.getEncoder().encodeToString(key))
        createdFiles.add(dataFolder.relativize(target).toString())
    }

    private fun createDirectoryIfMissing(target: Path, createdFiles: MutableList<String>) {
        if (Files.exists(target)) {
            return
        }

        Files.createDirectories(target)
        createdFiles.add(dataFolder.relativize(target).toString())
    }

    private fun fallbackDefault(resourceName: String): String? {
        return when (resourceName) {
            "defaults/custom-item.yml" -> DEFAULT_CUSTOM_ITEM_YML
            else -> null
        }
    }

    companion object {
        private const val DEFAULT_CUSTOM_ITEM_YML = """# 복잡한 아이템을 이름으로 등록하고 Api.yml의 custom-give에서 참조합니다.
# 문자열 필드는 색상 코드와 placeholder를 사용할 수 있습니다.
items:
  donation_diamond:
    material: "DIAMOND"
    amount: 1
    name: "&b후원 다이아몬드"
    lore:
      - "&7후원자: {donator}"
      - "&7금액: {amount}"
    glow: true
    persistentData:
      - key: "mcstreamapi:item_id"
        type: "string"
        value: "donation_diamond"
      - key: "mcstreamapi:donation_amount"
        type: "long"
        value: 1000

  rare_emerald:
    material: "EMERALD"
    amount: "<1..3>"
    name: "&a희귀 에메랄드"
    enchantments:
      minecraft:unbreaking: 1
    itemFlags:
      - "HIDE_ENCHANTS"
"""
    }
}
