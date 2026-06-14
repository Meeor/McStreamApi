package kr.meeor.mcstreamapi.donation.chzzk

import kr.meeor.mcstreamapi.token.OAuthToken
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ChzzkTokenRefresher(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenEndpoint: String = DEFAULT_TOKEN_ENDPOINT,
    private val refreshBeforeSeconds: Long = 300,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun shouldRefresh(token: OAuthToken): Boolean {
        return token.expiresAtEpochSeconds - Instant.now(clock).epochSecond <= refreshBeforeSeconds
    }

    fun refresh(token: OAuthToken): Result<OAuthToken> {
        return runCatching {
            val body = """
                {"grantType":"refresh_token","refreshToken":"${token.refreshToken}","clientId":"$clientId","clientSecret":"$clientSecret"}
            """.trimIndent()
            val response = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            if (response.statusCode() !in 200..299) {
                throw ChzzkDonationProviderException("CHZZK_TOKEN_REFRESH_FAILED")
            }

            val root = Json.parseToJsonElement(response.body()).jsonObject
            val accessToken = root["accessToken"]?.jsonPrimitive?.contentOrNull
                ?: throw ChzzkDonationProviderException("CHZZK_ACCESS_TOKEN_MISSING")
            val refreshToken = root["refreshToken"]?.jsonPrimitive?.contentOrNull ?: token.refreshToken
            val expiresIn = root["expiresIn"]?.jsonPrimitive?.longOrNull ?: DEFAULT_EXPIRES_IN_SECONDS

            token.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSeconds = Instant.now(clock).plusSeconds(expiresIn).epochSecond,
            )
        }
    }

    companion object {
        private const val DEFAULT_TOKEN_ENDPOINT = "https://openapi.chzzk.naver.com/auth/v1/token"
        private const val DEFAULT_EXPIRES_IN_SECONDS = 86400L
    }
}
