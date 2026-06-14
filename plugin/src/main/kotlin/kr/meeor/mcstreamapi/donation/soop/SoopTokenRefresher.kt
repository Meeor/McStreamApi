package kr.meeor.mcstreamapi.donation.soop

import kr.meeor.mcstreamapi.token.OAuthToken
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SoopTokenRefresher(
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
            val response = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(refreshBody(token.refreshToken)))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            if (response.statusCode() !in 200..299) {
                throw SoopDonationProviderException("SOOP_TOKEN_REFRESH_FAILED")
            }

            val root = Json.parseToJsonElement(response.body()).jsonObject
            val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
                ?: root["accessToken"]?.jsonPrimitive?.contentOrNull
                ?: throw SoopDonationProviderException("SOOP_ACCESS_TOKEN_MISSING")
            val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                ?: root["refreshToken"]?.jsonPrimitive?.contentOrNull
                ?: token.refreshToken
            val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull
                ?: root["expiresIn"]?.jsonPrimitive?.longOrNull
                ?: DEFAULT_EXPIRES_IN_SECONDS

            token.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSeconds = Instant.now(clock).plusSeconds(expiresIn).epochSecond,
            )
        }
    }

    private fun refreshBody(refreshToken: String): String {
        val values = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to clientId,
            "client_secret" to clientSecret,
        )
        return values.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        private const val DEFAULT_TOKEN_ENDPOINT = "https://openapi.sooplive.com/auth/token"
        private const val DEFAULT_EXPIRES_IN_SECONDS = 3600L
    }
}
