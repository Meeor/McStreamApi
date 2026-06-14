package kr.meeor.mcstreamapi.authserver.config

import java.net.URI

class ConfigValidator {
    fun validate(config: AppConfig): ValidationResult {
        val errors = mutableListOf<String>()
        val disabledPlatforms = linkedMapOf<String, String>()

        validateServer(config.server, errors)
        validateSecurity(config.security, errors)
        validateHttp(config.http, errors)
        validateCleanup(config.cleanup, errors)

        val enabledPlatforms = config.platforms
            .filterValues { it.enabled }
            .filter { (platform, platformConfig) ->
                val reason = platformDisableReason(config.server, platform, platformConfig)
                if (reason != null) {
                    disabledPlatforms[platform] = reason
                    false
                } else {
                    true
                }
            }
            .keys
            .toSet()

        if (enabledPlatforms.isEmpty()) {
            errors += "NO_ENABLED_PLATFORM"
        }

        return ValidationResult(errors, enabledPlatforms, disabledPlatforms)
    }

    private fun validateServer(server: ServerConfig, errors: MutableList<String>) {
        if (server.host.isBlank()) {
            errors += "SERVER_HOST_MISSING"
        }

        if (server.port !in 1..65535) {
            errors += "SERVER_PORT_INVALID"
        }

        if (server.publicBaseUrl.isBlank()) {
            errors += "PUBLIC_BASE_URL_MISSING"
            return
        }

        val uri = runCatching { URI(server.publicBaseUrl) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) {
            errors += "PUBLIC_BASE_URL_INVALID"
            return
        }

        val isLocalhost = uri.host.equals("localhost", ignoreCase = true) || uri.host == "127.0.0.1"
        if (uri.scheme != "https" && !(server.allowInsecureLocalhost && isLocalhost)) {
            errors += "PUBLIC_BASE_URL_HTTPS_REQUIRED"
        }
    }

    private fun validateSecurity(security: SecurityConfig, errors: MutableList<String>) {
        when {
            security.sharedSecret.isBlank() -> errors += "SHARED_SECRET_MISSING"
            security.sharedSecret == DEFAULT_SHARED_SECRET -> errors += "SHARED_SECRET_PLACEHOLDER"
            security.sharedSecret.length < 32 -> errors += "SHARED_SECRET_TOO_SHORT"
        }
    }

    private fun validateHttp(http: HttpConfig, errors: MutableList<String>) {
        if (http.requestTimeoutSeconds !in 1..300) {
            errors += "REQUEST_TIMEOUT_INVALID"
        }
        if (http.shutdownTimeoutSeconds !in 1..300) {
            errors += "SHUTDOWN_TIMEOUT_INVALID"
        }
    }

    private fun validateCleanup(cleanup: CleanupConfig, errors: MutableList<String>) {
        if (cleanup.intervalSeconds !in 1..3600) {
            errors += "CLEANUP_INTERVAL_INVALID"
        }
        if (cleanup.expiredSessionRetainSeconds < 0) {
            errors += "EXPIRED_SESSION_RETAIN_INVALID"
        }
        if (cleanup.consumedSessionRetainSeconds < 0) {
            errors += "CONSUMED_SESSION_RETAIN_INVALID"
        }
        if (cleanup.failedSessionRetainSeconds < 0) {
            errors += "FAILED_SESSION_RETAIN_INVALID"
        }
    }

    private fun platformDisableReason(
        server: ServerConfig,
        platform: String,
        config: PlatformConfig,
    ): String? {
        if (config.clientId.isBlank()) {
            return "CLIENT_ID_MISSING"
        }

        if (config.clientSecret.isBlank()) {
            return "CLIENT_SECRET_MISSING"
        }

        if (config.redirectUri.isBlank()) {
            return "REDIRECT_URI_MISSING"
        }

        if (config.clientId.isPlaceholder("$platform client id")) {
            return "CLIENT_ID_PLACEHOLDER"
        }

        if (config.clientSecret.isPlaceholder("$platform client secret")) {
            return "CLIENT_SECRET_PLACEHOLDER"
        }

        val publicBase = server.publicBaseUrl.trimEnd('/')
        if (!config.redirectUri.startsWith("$publicBase/oauth/$platform/callback")) {
            return "REDIRECT_URI_MISMATCH"
        }

        if (platform.equals("soop", ignoreCase = true)) {
            return soopEndpointDisableReason(config.oauth)
        }

        return null
    }

    private fun soopEndpointDisableReason(oauth: PlatformOAuthConfig): String? {
        return when {
            oauth.authorizeEndpoint.isBlank() -> "SOOP_AUTHORIZE_ENDPOINT_MISSING"
            oauth.tokenEndpoint.isBlank() -> "SOOP_TOKEN_ENDPOINT_MISSING"
            oauth.channelInfoEndpoint.isBlank() -> "SOOP_CHANNEL_INFO_ENDPOINT_MISSING"
            !oauth.authorizeEndpoint.isHttpsUrl() -> "SOOP_AUTHORIZE_ENDPOINT_INVALID"
            !oauth.tokenEndpoint.isHttpsUrl() -> "SOOP_TOKEN_ENDPOINT_INVALID"
            oauth.refreshEndpoint.isNotBlank() && !oauth.refreshEndpoint.isHttpsUrl() -> "SOOP_REFRESH_ENDPOINT_INVALID"
            !oauth.channelInfoEndpoint.isHttpsUrl() -> "SOOP_CHANNEL_INFO_ENDPOINT_INVALID"
            oauth.authorizeEndpoint.isPlaceholder("soop authorize endpoint") -> "SOOP_AUTHORIZE_ENDPOINT_PLACEHOLDER"
            oauth.tokenEndpoint.isPlaceholder("soop token endpoint") -> "SOOP_TOKEN_ENDPOINT_PLACEHOLDER"
            oauth.channelInfoEndpoint.isPlaceholder("soop channel info endpoint") -> "SOOP_CHANNEL_INFO_ENDPOINT_PLACEHOLDER"
            else -> null
        }
    }

    private fun String.isHttpsUrl(): Boolean {
        val uri = runCatching { URI(this) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank()
    }

    private fun String.isPlaceholder(label: String): Boolean {
        val normalized = uppercase()
        return normalized == DEFAULT_SHARED_SECRET ||
            normalized == label.uppercase() ||
            normalized.endsWith("_CLIENT_ID") ||
            normalized.endsWith("_CLIENT_SECRET") ||
            normalized.endsWith("_ENDPOINT") ||
            normalized.contains("CHANGE_ME") ||
            normalized.contains("PLACEHOLDER")
    }

    companion object {
        private const val DEFAULT_SHARED_SECRET = "CHANGE_ME_RANDOM_LONG_SECRET"
    }
}

data class ValidationResult(
    val errors: List<String>,
    val enabledPlatforms: Set<String>,
    val disabledPlatforms: Map<String, String>,
) {
    val isValid: Boolean = errors.isEmpty()
}
