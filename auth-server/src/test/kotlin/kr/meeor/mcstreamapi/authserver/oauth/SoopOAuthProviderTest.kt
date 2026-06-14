package kr.meeor.mcstreamapi.authserver.oauth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.runBlocking
import kr.meeor.mcstreamapi.authserver.config.PlatformConfig
import kr.meeor.mcstreamapi.authserver.config.PlatformOAuthConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class SoopOAuthProviderTest {
    @Test
    fun `authorize URL includes oauth parameters`() {
        val provider = SoopOAuthProvider(platformConfig(), FakeTransport())

        val url = provider.buildAuthorizeUrl("state-1")

        assertEquals(
            "https://openapi.sooplive.com/auth/code?client_id=client-id",
            url,
        )
    }

    @Test
    fun `token and channel responses map to common models`() = runBlocking {
        val provider = SoopOAuthProvider(
            config = platformConfig(),
            transport = FakeTransport(),
            clock = Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), ZoneOffset.UTC),
        )

        val token = provider.exchangeCodeForToken("code-1", "state-1")
        val channel = provider.fetchChannelInfo(token.accessToken)

        assertEquals("access-token", token.accessToken)
        assertEquals("refresh-token", token.refreshToken)
        assertEquals("Bearer", token.tokenType)
        assertEquals(setOf("api"), token.scopes)
        assertEquals(Instant.parse("2026-06-10T08:00:00Z"), token.expiresAt)
        assertEquals("soop", channel.platform)
        assertEquals("Meeor", channel.channelId)
        assertEquals("Meeor", channel.channelName)
    }

    private fun platformConfig(): PlatformConfig =
        PlatformConfig(
            enabled = true,
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://auth.example.com/mca/oauth/soop/callback",
            scopes = listOf("api"),
            oauth = PlatformOAuthConfig(
                authorizeEndpoint = "https://openapi.sooplive.com/auth/code",
                tokenEndpoint = "https://openapi.sooplive.com/auth/token",
                refreshEndpoint = "https://openapi.sooplive.com/auth/token",
                channelInfoEndpoint = "https://openapi.sooplive.com/user/stationinfo",
            ),
        )

    private class FakeTransport : SoopOAuthTransport {
        private val json = Json

        override suspend fun exchangeCode(config: PlatformConfig, code: String): JsonElement {
            assertEquals("code-1", code)
            return json.parseToJsonElement(
                """
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "token_type": "Bearer",
                  "expires_in": 28800,
                  "scope": null
                }
                """.trimIndent(),
            )
        }

        override suspend fun fetchChannelInfo(config: PlatformConfig, accessToken: String): JsonElement {
            assertEquals("access-token", accessToken)
            return json.parseToJsonElement(
                """
                {
                  "result": 1,
                  "data": {
                    "user_nick": "Meeor",
                    "station_name": "Meeor station"
                  }
                }
                """.trimIndent(),
            )
        }
    }
}
