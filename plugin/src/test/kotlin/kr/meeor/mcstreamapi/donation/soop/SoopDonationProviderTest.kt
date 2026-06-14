package kr.meeor.mcstreamapi.donation.soop

import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoopDonationProviderTest {
    @Test
    fun `converts transport dto through listener`() {
        val root = Files.createTempDirectory("mcstreamapi-soop-provider")
        val transport = FakeTransport(
            dto = SoopDonationDto(
                eventId = "event-1",
                streamerId = "streamer-id",
                streamerName = "streamer",
                donatorName = "donator",
                amount = 1000,
                message = null,
                occurredAtEpochSeconds = 123,
            ),
        )
        val provider = SoopDonationProvider(
            tokenRefresher = SoopTokenRefresher(
                clientId = "client",
                clientSecret = "secret",
                clock = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC),
            ),
            tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
            sessionTransport = transport,
        )
        val received = mutableListOf<String>()

        provider.startSession(
            minecraftUuid = "uuid",
            streamerId = "streamer-id",
            streamerName = "streamer",
            token = token(expiresAt = 1000),
        ) { event -> received.add("${event.platform}:${event.eventId}:${event.amount}") }

        assertEquals(listOf("soop:event-1:1000"), received)
    }

    @Test
    fun `default transport blocks until official event api is configured`() {
        val root = Files.createTempDirectory("mcstreamapi-soop-provider")
        val provider = SoopDonationProvider(
            tokenRefresher = SoopTokenRefresher(clientId = "client", clientSecret = "secret"),
            tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
        )

        val exception = assertFailsWith<SoopDonationProviderException> {
            provider.startSession("uuid", "streamer-id", "streamer", token(expiresAt = Long.MAX_VALUE)) {}
        }

        assertEquals("SOOP_EVENT_API_NOT_CONFIGURED", exception.code)
    }

    private fun token(expiresAt: Long): OAuthToken {
        return OAuthToken(
            platform = "soop",
            minecraftUuid = "uuid",
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochSeconds = expiresAt,
        )
    }

    private class FakeTransport(
        private val dto: SoopDonationDto,
    ) : SoopDonationSessionTransport {
        override fun open(
            token: OAuthToken,
            streamerId: String,
            streamerName: String,
            playerName: String,
            reconnectPolicy: ProviderReconnectPolicy,
            listener: (SoopDonationDto) -> Unit,
        ): SoopDonationSession {
            listener(dto)
            return SoopDonationSession { }
        }
    }
}
