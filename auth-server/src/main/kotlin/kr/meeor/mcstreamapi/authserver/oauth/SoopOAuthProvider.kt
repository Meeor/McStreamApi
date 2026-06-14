package kr.meeor.mcstreamapi.authserver.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.meeor.mcstreamapi.authserver.config.PlatformConfig
import kr.meeor.mcstreamapi.authserver.pairing.ChannelInfo
import kr.meeor.mcstreamapi.authserver.pairing.OAuthToken
import java.time.Clock
import java.time.Instant

class SoopOAuthProvider(
    private val config: PlatformConfig,
    private val transport: SoopOAuthTransport = KtorSoopOAuthTransport(),
    private val clock: Clock = Clock.systemUTC(),
) : OAuthProvider {
    override val platform: String = "soop"
    override val supportsState: Boolean = false

    override fun buildAuthorizeUrl(state: String): String =
        URLBuilder(config.oauth.authorizeEndpoint).apply {
            parameters.append("client_id", config.clientId)
        }.buildString()

    override suspend fun exchangeCodeForToken(code: String, state: String): OAuthToken {
        val body = transport.exchangeCode(config, code)
        val token = runCatching { body.jsonObject }
            .getOrElse { throw OAuthProviderException("SOOP_TOKEN_RESPONSE_INVALID", "SOOP token response is not a JSON object.", it) }

        val accessToken = token.stringValue("access_token", "accessToken")
            ?: throw OAuthProviderException("SOOP_ACCESS_TOKEN_MISSING", "SOOP token response has no access token.")
        val expiresIn = token.longValue("expires_in", "expiresIn") ?: DEFAULT_EXPIRES_IN_SECONDS

        return OAuthToken(
            accessToken = accessToken,
            refreshToken = token.stringValue("refresh_token", "refreshToken"),
            tokenType = token.stringValue("token_type", "tokenType") ?: "Bearer",
            scopes = token.scopeValues(config.scopes),
            expiresAt = Instant.now(clock).plusSeconds(expiresIn),
        )
    }

    override suspend fun fetchChannelInfo(accessToken: String): ChannelInfo {
        val body = transport.fetchChannelInfo(config, accessToken)
        val root = runCatching { body.jsonObject }
            .getOrElse { throw OAuthProviderException("SOOP_CHANNEL_RESPONSE_INVALID", "SOOP channel response is not a JSON object.", it) }
        val result = root.longValue("result")
        if (result != null && result != 1L) {
            throw OAuthProviderException("SOOP_CHANNEL_INFO_FAILED", "SOOP channel endpoint returned result=$result.")
        }
        val channel = root.objectValue("data") ?: root

        val channelId = channel.stringValue("user_id", "userId", "channel_id", "channelId", "user_nick", "userNick")
            ?: throw OAuthProviderException("SOOP_CHANNEL_ID_MISSING", "SOOP channel response has no channel id.")
        val channelName = channel.stringValue("user_nick", "userNick", "station_name", "stationName", "channel_name", "channelName")
            ?: channelId

        return ChannelInfo(
            platform = platform,
            channelId = channelId,
            channelName = channelName,
        )
    }

    private companion object {
        private const val DEFAULT_EXPIRES_IN_SECONDS = 3600L
    }
}

interface SoopOAuthTransport {
    suspend fun exchangeCode(config: PlatformConfig, code: String): JsonElement

    suspend fun fetchChannelInfo(config: PlatformConfig, accessToken: String): JsonElement
}

class KtorSoopOAuthTransport(
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SoopOAuthTransport {
    override suspend fun exchangeCode(config: PlatformConfig, code: String): JsonElement {
        val response = client.post(config.oauth.tokenEndpoint) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("client_id", config.clientId)
                        append("client_secret", config.clientSecret)
                        append("redirect_uri", config.redirectUri)
                    },
                ),
            )
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OAuthProviderException("SOOP_TOKEN_EXCHANGE_FAILED", "SOOP token endpoint returned HTTP ${response.status.value}.")
        }

        return json.decodeFromString<JsonElement>(body)
    }

    override suspend fun fetchChannelInfo(config: PlatformConfig, accessToken: String): JsonElement {
        val response = client.post(config.oauth.channelInfoEndpoint) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("access_token", accessToken)
                    },
                ),
            )
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OAuthProviderException("SOOP_CHANNEL_INFO_FAILED", "SOOP channel endpoint returned HTTP ${response.status.value}.")
        }

        return json.decodeFromString<JsonElement>(body)
    }
}

private fun JsonObject.objectValue(key: String): JsonObject? =
    this[key]?.jsonObjectOrNull()

private fun JsonObject.stringValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }

private fun JsonObject.longValue(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() }

private fun JsonObject.scopeValues(fallback: List<String>): Set<String> {
    val raw = stringValue("scope", "scopes")
        ?: return fallback.toSet()

    return raw.split(' ', ',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()
