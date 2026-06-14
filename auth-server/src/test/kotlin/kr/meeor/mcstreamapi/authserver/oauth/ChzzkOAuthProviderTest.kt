package kr.meeor.mcstreamapi.authserver.oauth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kr.meeor.mcstreamapi.authserver.config.PlatformConfig
import kr.meeor.mcstreamapi.authserver.config.PlatformOAuthConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ChzzkOAuthProviderTest {
    @Test
    fun `authorize URL uses official account interlock endpoint`() {
        val provider = ChzzkOAuthProvider(platformConfig(), FakeTransport())

        val url = provider.buildAuthorizeUrl("state-1")

        assertEquals(
            "https://chzzk.naver.com/account-interlock?clientId=client-id&redirectUri=https%3A%2F%2Fauth.example.com%2Fmca%2Foauth%2Fchzzk%2Fcallback&state=state-1",
            url,
        )
    }

    @Test
    fun `token and user responses map to common models`() = runBlocking {
        val provider = ChzzkOAuthProvider(
            config = platformConfig(),
            transport = FakeTransport(),
            clock = Clock.fixed(Instant.parse("2026-06-10T00:00:00Z"), ZoneOffset.UTC),
        )

        val token = provider.exchangeCodeForToken("code-1", "state-1")
        val channel = provider.fetchChannelInfo(token.accessToken)

        assertEquals("access-token", token.accessToken)
        assertEquals("refresh-token", token.refreshToken)
        assertEquals("Bearer", token.tokenType)
        assertEquals(setOf("user:read"), token.scopes)
        assertEquals(Instant.parse("2026-06-11T00:00:00Z"), token.expiresAt)
        assertEquals("chzzk", channel.platform)
        assertEquals("channel-1", channel.channelId)
        assertEquals("Meeor", channel.channelName)
    }

    private fun platformConfig(): PlatformConfig =
        PlatformConfig(
            enabled = true,
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "https://auth.example.com/mca/oauth/chzzk/callback",
            scopes = listOf("user:read"),
            oauth = PlatformOAuthConfig(
                authorizeEndpoint = "https://chzzk.naver.com/account-interlock",
                tokenEndpoint = "https://openapi.chzzk.naver.com/auth/v1/token",
                refreshEndpoint = "https://openapi.chzzk.naver.com/auth/v1/token",
                channelInfoEndpoint = "https://openapi.chzzk.naver.com/open/v1/users/me",
            ),
        )

    private class FakeTransport : ChzzkOAuthTransport {
        private val json = Json

        override suspend fun exchangeCode(config: PlatformConfig, code: String, state: String): JsonElement {
            assertEquals("code-1", code)
            assertEquals("state-1", state)
            return json.parseToJsonElement(
                """
                {
                  "code": 200,
                  "message": null,
                  "content": {
                    "accessToken": "access-token",
                    "refreshToken": "refresh-token",
                    "tokenType": "Bearer",
                    "expiresIn": "86400",
                    "scope": "user:read"
                  }
                }
                """.trimIndent(),
            )
        }

        override suspend fun fetchUserInfo(config: PlatformConfig, accessToken: String): JsonElement {
            assertEquals("access-token", accessToken)
            return json.parseToJsonElement(
                """
                {
                  "code": 200,
                  "message": null,
                  "content": {
                    "channelId": "channel-1",
                    "channelName": "Meeor"
                  }
                }
                """.trimIndent(),
            )
        }
    }
}
