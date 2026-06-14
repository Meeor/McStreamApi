package kr.meeor.mcstreamapi.config

import org.yaml.snakeyaml.Yaml
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class PluginConfigValidator {
    fun validate(configPath: Path): ConfigValidationResult {
        if (!Files.exists(configPath)) {
            return ConfigValidationResult(
                authAvailable = false,
                enabledPlatforms = emptySet(),
                disabledPlatforms = SUPPORTED_PLATFORMS,
                warnings = listOf("CONFIG_MISSING config.yml was not found."),
            )
        }

        val root = runCatching {
            Files.newInputStream(configPath).use { input ->
                Yaml().load<Map<String, Any?>>(input) ?: emptyMap()
            }
        }.getOrElse {
            return ConfigValidationResult(
                authAvailable = false,
                enabledPlatforms = emptySet(),
                disabledPlatforms = SUPPORTED_PLATFORMS,
                warnings = listOf("CONFIG_INVALID_YAML config.yml could not be parsed."),
            )
        }

        val warnings = mutableListOf<String>()
        val logging = root.map("logging")
        val loggingConfig = PluginLoggingConfig(
            debug = logging.boolean("debug", default = false),
        )

        val auth = root.map("auth")
        val serverBaseUrl = auth.string("serverBaseUrl")
        val sharedSecret = auth.string("sharedSecret")
        val authAvailable = validateAuth(serverBaseUrl, sharedSecret, warnings)
        val authConfig = if (authAvailable) {
            PluginAuthConfig(
                serverBaseUrl = serverBaseUrl!!.trim().trimEnd('/'),
                sharedSecret = sharedSecret!!.trim(),
                pollingIntervalSeconds = (auth.long("pollingIntervalSeconds") ?: 3).coerceAtLeast(1),
                pairingTimeoutSeconds = (auth.long("pairingTimeoutSeconds") ?: 600).coerceAtLeast(30),
            )
        } else {
            null
        }

        val platforms = root.map("platforms")
        val enabledPlatforms = mutableSetOf<String>()
        val disabledPlatforms = mutableSetOf<String>()
        val platformConfigs = mutableMapOf<String, PluginPlatformConfig>()

        SUPPORTED_PLATFORMS.forEach { platform ->
            val platformConfig = platforms.map(platform)
            val requested = platformConfig.boolean("enabled", default = false)
            val clientId = platformConfig.string("clientId")
            val clientSecret = platformConfig.string("clientSecret")

            if (!requested) {
                disabledPlatforms.add(platform)
                return@forEach
            }

            if (isPlaceholder(clientId) || isPlaceholder(clientSecret)) {
                disabledPlatforms.add(platform)
                warnings.add("PLATFORM_DISABLED platform=$platform reason=MISSING_CLIENT_CREDENTIALS")
            } else {
                enabledPlatforms.add(platform)
                platformConfigs[platform] = PluginPlatformConfig(
                    clientId = clientId!!.trim(),
                    clientSecret = clientSecret!!.trim(),
                    eventPollingIntervalSeconds = (platformConfig.long("eventPollingIntervalSeconds") ?: 5).coerceAtLeast(1),
                    tokenRefreshBeforeSeconds = (platformConfig.long("tokenRefreshBeforeSeconds") ?: 300).coerceAtLeast(30),
                    receiveAdBalloons = platformConfig.boolean("receiveAdBalloons", default = false),
                    receiveVideoBalloons = platformConfig.boolean("receiveVideoBalloons", default = false),
                )
            }
        }

        return ConfigValidationResult(
            authAvailable = authAvailable,
            enabledPlatforms = enabledPlatforms,
            disabledPlatforms = disabledPlatforms,
            warnings = warnings,
            authConfig = authConfig,
            platformConfigs = platformConfigs,
            loggingConfig = loggingConfig,
        )
    }

    private fun validateAuth(
        serverBaseUrl: String?,
        sharedSecret: String?,
        warnings: MutableList<String>,
    ): Boolean {
        var valid = true

        if (!isValidServerBaseUrl(serverBaseUrl)) {
            warnings.add("AUTH_DISABLED reason=INVALID_SERVER_BASE_URL")
            valid = false
        }

        if (isPlaceholder(sharedSecret) || sharedSecret.orEmpty().length < MIN_SHARED_SECRET_LENGTH) {
            warnings.add("AUTH_DISABLED reason=INVALID_SHARED_SECRET")
            valid = false
        }

        return valid
    }

    private fun isValidServerBaseUrl(value: String?): Boolean {
        if (isPlaceholder(value)) {
            return false
        }

        return runCatching {
            val uri = URI(value)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun isPlaceholder(value: String?): Boolean {
        val normalized = value.orEmpty().trim()
        return normalized.isBlank() ||
            normalized.contains("CHANGE_ME", ignoreCase = true) ||
            normalized.endsWith("_CLIENT_ID") ||
            normalized.endsWith("_CLIENT_SECRET")
    }

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this[key] as? Map<String, Any?> ?: emptyMap()
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key]?.toString()

    private fun Map<String, Any?>.long(key: String): Long? {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun Map<String, Any?>.boolean(key: String, default: Boolean): Boolean {
        return when (val value = this[key]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }

    companion object {
        private const val MIN_SHARED_SECRET_LENGTH = 32
        private val SUPPORTED_PLATFORMS = setOf("chzzk", "soop")
    }
}
