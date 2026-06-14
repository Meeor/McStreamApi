package kr.meeor.mcstreamapi.donation.soop

import com.sun.net.httpserver.HttpServer
import kr.meeor.mcstreamapi.token.OAuthToken
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoopTokenRefresherTest {
    @Test
    fun `detects refresh window`() {
        val refresher = SoopTokenRefresher(
            clientId = "client",
            clientSecret = "secret",
            refreshBeforeSeconds = 300,
            clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
        )

        assertTrue(refresher.shouldRefresh(token(expiresAt = 1200)))
        assertFalse(refresher.shouldRefresh(token(expiresAt = 1400)))
    }

    @Test
    fun `refreshes token`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requestBody = ""
        server.createContext("/auth/token") { exchange ->
            requestBody = exchange.requestBody.bufferedReader().readText()
            val body = """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":60}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val endpoint = "http://127.0.0.1:${server.address.port}/auth/token"
            val refresher = SoopTokenRefresher(
                clientId = "client",
                clientSecret = "secret",
                tokenEndpoint = endpoint,
                clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
            )

            val refreshed = refresher.refresh(token(expiresAt = 1000)).getOrThrow()

            assertEquals("new-access", refreshed.accessToken)
            assertEquals("new-refresh", refreshed.refreshToken)
            assertEquals(1060, refreshed.expiresAtEpochSeconds)
            assertTrue(requestBody.contains("grant_type=refresh_token"))
            assertTrue(requestBody.contains("refresh_token=old-refresh"))
        } finally {
            server.stop(0)
        }
    }

    private fun token(expiresAt: Long): OAuthToken {
        return OAuthToken(
            platform = "soop",
            minecraftUuid = "uuid",
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtEpochSeconds = expiresAt,
        )
    }
}
