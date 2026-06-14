package kr.meeor.mcstreamapi.authserver.config

data class AppConfig(
    val server: ServerConfig,
    val security: SecurityConfig,
    val http: HttpConfig,
    val cleanup: CleanupConfig,
    val platforms: Map<String, PlatformConfig>,
)

data class ServerConfig(
    val host: String,
    val port: Int,
    val publicBaseUrl: String,
    val allowInsecureLocalhost: Boolean,
)

data class SecurityConfig(
    val sharedSecret: String,
    val pairingExpireSeconds: Long,
    val stateExpireSeconds: Long,
    val enableRateLimit: Boolean,
    val trustedProxyHeaders: Boolean,
)

data class HttpConfig(
    val requestTimeoutSeconds: Long,
    val shutdownTimeoutSeconds: Long,
)

data class CleanupConfig(
    val intervalSeconds: Long,
    val expiredSessionRetainSeconds: Long,
    val consumedSessionRetainSeconds: Long,
    val failedSessionRetainSeconds: Long,
)

data class PlatformConfig(
    val enabled: Boolean,
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String>,
    val oauth: PlatformOAuthConfig,
)

data class PlatformOAuthConfig(
    val authorizeEndpoint: String,
    val tokenEndpoint: String,
    val refreshEndpoint: String,
    val channelInfoEndpoint: String,
)

data class ValidatedConfig(
    val config: AppConfig,
    val enabledPlatforms: Set<String>,
    val disabledPlatforms: Map<String, String>,
)
