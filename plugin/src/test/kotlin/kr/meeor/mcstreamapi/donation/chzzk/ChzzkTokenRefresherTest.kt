package kr.meeor.mcstreamapi.donation.chzzk

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

class ChzzkTokenRefresherTest {
    @Test
    fun `detects refresh window`() {
        val refresher = ChzzkTokenRefresher(
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
        server.createContext("/auth/v1/token") { exchange ->
            val body = """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":"86400"}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val refresher = ChzzkTokenRefresher(
                clientId = "client",
                clientSecret = "secret",
                tokenEndpoint = "http://127.0.0.1:${server.address.port}/auth/v1/token",
                clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC),
            )

            val refreshed = refresher.refresh(token(expiresAt = 1000)).getOrThrow()

            assertEquals("new-access", refreshed.accessToken)
            assertEquals("new-refresh", refreshed.refreshToken)
            assertEquals(87400, refreshed.expiresAtEpochSeconds)
        } finally {
            server.stop(0)
        }
    }

    private fun token(expiresAt: Long): OAuthToken {
        return OAuthToken(
            platform = "chzzk",
            minecraftUuid = "uuid",
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtEpochSeconds = expiresAt,
        )
    }
}
