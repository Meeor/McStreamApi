package kr.meeor.mcstreamapi.config

data class ConfigValidationResult(
    val authAvailable: Boolean,
    val enabledPlatforms: Set<String>,
    val disabledPlatforms: Set<String>,
    val warnings: List<String>,
    val authConfig: PluginAuthConfig? = null,
    val platformConfigs: Map<String, PluginPlatformConfig> = emptyMap(),
    val loggingConfig: PluginLoggingConfig = PluginLoggingConfig(),
    val streamerRewardsEnabled: Boolean = false,
)

data class PluginAuthConfig(
    val serverBaseUrl: String,
    val sharedSecret: String,
    val pollingIntervalSeconds: Long,
    val pairingTimeoutSeconds: Long,
)

data class PluginPlatformConfig(
    val clientId: String,
    val clientSecret: String,
    val eventPollingIntervalSeconds: Long,
    val tokenRefreshBeforeSeconds: Long,
    val receiveAdBalloons: Boolean = false,
    val receiveVideoBalloons: Boolean = false,
)

data class PluginLoggingConfig(
    val debug: Boolean = false,
)
