package kr.meeor.mcstreamapi.authserver.config

import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class ConfigLoader {
    fun ensureConfigExists(configPath: Path): Boolean {
        if (configPath.exists()) {
            return true
        }

        configPath.parent?.createDirectories()

        val resource = javaClass.classLoader.getResourceAsStream("config.example.yml")
            ?: error("Bundled config.example.yml resource is missing.")

        resource.use { input ->
            configPath.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return false
    }

    fun load(configPath: Path): AppConfig {
        configPath.inputStream().use { input ->
            return parse(input)
        }
    }

    private fun parse(input: InputStream): AppConfig {
        val root = Yaml().load<Map<String, Any?>>(input)
            ?: throw ConfigException("CONFIG_EMPTY", "config.yml is empty.")

        val server = root.map("server")
        val security = root.map("security")
        val http = root.optionalMap("http")
        val cleanup = root.optionalMap("cleanup")
        val platforms = root.map("platforms")

        return AppConfig(
            server = ServerConfig(
                host = server.string("host", "127.0.0.1"),
                port = server.int("port", 18080),
                publicBaseUrl = server.string("publicBaseUrl", ""),
                allowInsecureLocalhost = server.boolean("allowInsecureLocalhost", false),
            ),
            security = SecurityConfig(
                sharedSecret = security.string("sharedSecret", ""),
                pairingExpireSeconds = security.long("pairingExpireSeconds", 600),
                stateExpireSeconds = security.long("stateExpireSeconds", 600),
                enableRateLimit = security.boolean("enableRateLimit", true),
                trustedProxyHeaders = security.boolean("trustedProxyHeaders", true),
            ),
            http = HttpConfig(
                requestTimeoutSeconds = http.long("requestTimeoutSeconds", 15),
                shutdownTimeoutSeconds = http.long("shutdownTimeoutSeconds", 10),
            ),
            cleanup = CleanupConfig(
                intervalSeconds = cleanup.long("intervalSeconds", 60),
                expiredSessionRetainSeconds = cleanup.long("expiredSessionRetainSeconds", 300),
                consumedSessionRetainSeconds = cleanup.long("consumedSessionRetainSeconds", 60),
                failedSessionRetainSeconds = cleanup.long("failedSessionRetainSeconds", 600),
            ),
            platforms = platforms.mapValues { (_, value) ->
                val platform = value.asMap()
                val oauth = platform.optionalMap("oauth")
                PlatformConfig(
                    enabled = platform.boolean("enabled", false),
                    clientId = platform.string("clientId", ""),
                    clientSecret = platform.string("clientSecret", ""),
                    redirectUri = platform.string("redirectUri", ""),
                    scopes = platform.stringList("scopes"),
                    oauth = PlatformOAuthConfig(
                        authorizeEndpoint = oauth.string("authorizeEndpoint", ""),
                        tokenEndpoint = oauth.string("tokenEndpoint", ""),
                        refreshEndpoint = oauth.string("refreshEndpoint", ""),
                        channelInfoEndpoint = oauth.string("channelInfoEndpoint", ""),
                    ),
                )
            },
        )
    }

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
        this[key].asMap(key)

    private fun Map<String, Any?>.optionalMap(key: String): Map<String, Any?> =
        this[key]?.asMap(key) ?: emptyMap()

    private fun Any?.asMap(name: String = "value"): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return this as? Map<String, Any?>
            ?: throw ConfigException("CONFIG_SECTION_INVALID", "$name must be a YAML object.")
    }

    private fun Map<String, Any?>.string(key: String, default: String): String =
        this[key]?.toString()?.trim() ?: default

    private fun Map<String, Any?>.int(key: String, default: Int): Int =
        when (val value = this[key]) {
            null -> default
            is Number -> value.toInt()
            else -> value.toString().toIntOrNull()
                ?: throw ConfigException("CONFIG_VALUE_INVALID", "$key must be an integer.")
        }

    private fun Map<String, Any?>.long(key: String, default: Long): Long =
        when (val value = this[key]) {
            null -> default
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull()
                ?: throw ConfigException("CONFIG_VALUE_INVALID", "$key must be a long.")
        }

    private fun Map<String, Any?>.boolean(key: String, default: Boolean): Boolean =
        when (val value = this[key]) {
            null -> default
            is Boolean -> value
            else -> value.toString().toBooleanStrictOrNull()
                ?: throw ConfigException("CONFIG_VALUE_INVALID", "$key must be a boolean.")
        }

    private fun Map<String, Any?>.stringList(key: String): List<String> {
        val value = this[key] ?: return emptyList()
        if (value !is List<*>) {
            throw ConfigException("CONFIG_VALUE_INVALID", "$key must be a list.")
        }

        return value.map { it?.toString()?.trim().orEmpty() }.filter { it.isNotBlank() }
    }
}

class ConfigException(
    val code: String,
    override val message: String,
) : RuntimeException(message)
