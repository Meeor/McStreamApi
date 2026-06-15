package kr.meeor.mcstreamapi.donation.chzzk

import com.sun.net.httpserver.HttpServer
import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChzzkDonationProviderTest {
    @Test
    fun `subscribes after socket connected and forwards donation event`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<String>()
        server.createContext("/open/v1/sessions/auth") { exchange ->
            requests.add(exchange.requestURI.path)
            val body = """{"url":"https://ssio.example.com?auth=token"}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.createContext("/open/v1/sessions/events/subscribe/donation") { exchange ->
            requests.add("${exchange.requestURI.rawPath}?${exchange.requestURI.rawQuery}")
            exchange.sendResponseHeaders(204, -1)
        }
        server.start()

        try {
            val root = Files.createTempDirectory("mcstreamapi-chzzk-provider")
            val transport = FakeTransport()
            val provider = ChzzkDonationProvider(
                tokenRefresher = ChzzkTokenRefresher(
                    clientId = "client",
                    clientSecret = "secret",
                    clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC),
                ),
                tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
                sessionApi = ChzzkSessionApi(baseUrl = "http://127.0.0.1:${server.address.port}"),
                sessionTransport = transport,
            )
            val received = mutableListOf<String>()

            provider.startSession(
                minecraftUuid = "uuid",
                streamerId = "channel",
                streamerName = "streamer",
                token = token(expiresAt = 1000),
            ) { event -> received.add("${event.platform}:${event.amount}") }

            transport.handler?.onConnected("session-key")
            transport.handler?.onDonation(
                ChzzkDonationDto(
                    donationType = "CHAT",
                    channelId = "channel",
                    donatorChannelId = "donator-channel",
                    donatorNickname = "donator",
                    payAmount = 1000,
                    donationText = null,
                    messageTime = 123,
                ),
            )

            assertEquals("https://ssio.example.com?auth=token", transport.sessionUrl)
            assertEquals(
                listOf(
                    "/open/v1/sessions/auth",
                    "/open/v1/sessions/events/subscribe/donation?sessionKey=session-key",
                ),
                requests,
            )
            assertEquals(listOf("chzzk:1000"), received)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `maps transport open failure to provider error`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/open/v1/sessions/auth") { exchange ->
            val body = """{"url":"https://ssio.example.com?auth=token"}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val root = Files.createTempDirectory("mcstreamapi-chzzk-provider")
            val provider = ChzzkDonationProvider(
                tokenRefresher = ChzzkTokenRefresher(
                    clientId = "client",
                    clientSecret = "secret",
                    clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC),
                ),
                tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
                sessionApi = ChzzkSessionApi(baseUrl = "http://127.0.0.1:${server.address.port}"),
                sessionTransport = FailingTransport(),
            )

            val error = assertFailsWith<ChzzkDonationProviderException> {
                provider.startSession(
                    minecraftUuid = "uuid",
                    streamerId = "channel",
                    streamerName = "streamer",
                    token = token(expiresAt = 1000),
                ) { }
            }

            assertEquals("CHZZK_PROVIDER_ERROR", error.code)
        } finally {
            server.stop(0)
        }
    }

    private fun token(expiresAt: Long): OAuthToken {
        return OAuthToken(
            platform = "chzzk",
            minecraftUuid = "uuid",
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochSeconds = expiresAt,
        )
    }

    private class FakeTransport : ChzzkSessionTransport {
        var sessionUrl: String? = null
        var handler: ChzzkSessionHandler? = null

        override fun open(
            sessionUrl: String,
            reconnectPolicy: ProviderReconnectPolicy,
            handler: ChzzkSessionHandler,
        ): ChzzkSession {
            this.sessionUrl = sessionUrl
            this.handler = handler
            return ChzzkSession { }
        }
    }

    private class FailingTransport : ChzzkSessionTransport {
        override fun open(
            sessionUrl: String,
            reconnectPolicy: ProviderReconnectPolicy,
            handler: ChzzkSessionHandler,
        ): ChzzkSession {
            error("socket disconnected")
        }
    }
}
