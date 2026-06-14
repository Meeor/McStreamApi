package kr.meeor.mcstreamapi.authserver.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kr.meeor.mcstreamapi.authserver.config.PlatformConfig
import kr.meeor.mcstreamapi.authserver.pairing.ChannelInfo
import kr.meeor.mcstreamapi.authserver.pairing.OAuthToken
import java.time.Clock
import java.time.Instant

class ChzzkOAuthProvider(
    private val config: PlatformConfig,
    private val transport: ChzzkOAuthTransport = KtorChzzkOAuthTransport(),
    private val clock: Clock = Clock.systemUTC(),
) : OAuthProvider {
    override val platform: String = "chzzk"

    override fun buildAuthorizeUrl(state: String): String =
        URLBuilder(AUTHORIZE_ENDPOINT).apply {
            parameters.append("clientId", config.clientId)
            parameters.append("redirectUri", config.redirectUri)
            parameters.append("state", state)
        }.buildString()

    override suspend fun exchangeCodeForToken(code: String, state: String): OAuthToken {
        val body = transport.exchangeCode(config, code, state)
        val token = body.contentObject() ?: body.jsonObjectOrError("CHZZK_TOKEN_RESPONSE_INVALID")

        val accessToken = token.stringValue("accessToken", "access_token")
            ?: throw OAuthProviderException("CHZZK_ACCESS_TOKEN_MISSING", "Chzzk token response has no access token.")
        val expiresIn = token.longValue("expiresIn", "expires_in") ?: DEFAULT_EXPIRES_IN_SECONDS

        return OAuthToken(
            accessToken = accessToken,
            refreshToken = token.stringValue("refreshToken", "refresh_token"),
            tokenType = token.stringValue("tokenType", "token_type") ?: "Bearer",
            scopes = token.scopeValues(config.scopes),
            expiresAt = Instant.now(clock).plusSeconds(expiresIn),
        )
    }

    override suspend fun fetchChannelInfo(accessToken: String): ChannelInfo {
        val body = transport.fetchUserInfo(config, accessToken)
        val channel = body.contentObject() ?: body.jsonObjectOrError("CHZZK_USER_RESPONSE_INVALID")

        val channelId = channel.stringValue("channelId", "channel_id")
            ?: throw OAuthProviderException("CHZZK_CHANNEL_ID_MISSING", "Chzzk user response has no channel id.")
        val channelName = channel.stringValue("channelName", "channel_name") ?: channelId

        return ChannelInfo(
            platform = platform,
            channelId = channelId,
            channelName = channelName,
        )
    }

    private companion object {
        private const val AUTHORIZE_ENDPOINT = "https://chzzk.naver.com/account-interlock"
        private const val DEFAULT_EXPIRES_IN_SECONDS = 86400L
    }
}

interface ChzzkOAuthTransport {
    suspend fun exchangeCode(config: PlatformConfig, code: String, state: String): JsonElement

    suspend fun fetchUserInfo(config: PlatformConfig, accessToken: String): JsonElement
}

class KtorChzzkOAuthTransport(
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ChzzkOAuthTransport {
    override suspend fun exchangeCode(config: PlatformConfig, code: String, state: String): JsonElement {
        val response = client.post(config.oauth.tokenEndpoint.ifBlank { "$OPEN_API_BASE/auth/v1/token" }) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("grantType", "authorization_code")
                    put("clientId", config.clientId)
                    put("clientSecret", config.clientSecret)
                    put("code", code)
                    put("state", state)
                },
            )
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OAuthProviderException("CHZZK_TOKEN_EXCHANGE_FAILED", "Chzzk token endpoint returned HTTP ${response.status.value}.")
        }

        return json.decodeFromString<JsonElement>(body)
    }

    override suspend fun fetchUserInfo(config: PlatformConfig, accessToken: String): JsonElement {
        val response = client.get(config.oauth.channelInfoEndpoint.ifBlank { "$OPEN_API_BASE/open/v1/users/me" }) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw OAuthProviderException("CHZZK_USER_INFO_FAILED", "Chzzk user endpoint returned HTTP ${response.status.value}.")
        }

        return json.decodeFromString<JsonElement>(body)
    }

    private companion object {
        private const val OPEN_API_BASE = "https://openapi.chzzk.naver.com"
    }
}

private fun JsonElement.contentObject(): JsonObject? =
    runCatching { jsonObject["content"]?.jsonObject }.getOrNull()

private fun JsonElement.jsonObjectOrError(errorCode: String): JsonObject =
    runCatching { jsonObject }
        .getOrElse { throw OAuthProviderException(errorCode, "Chzzk response is not a JSON object.", it) }

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
