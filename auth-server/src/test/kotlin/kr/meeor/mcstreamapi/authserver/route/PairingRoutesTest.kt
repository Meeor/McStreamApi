package kr.meeor.mcstreamapi.authserver.route

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kr.meeor.mcstreamapi.authserver.config.AppConfig
import kr.meeor.mcstreamapi.authserver.config.CleanupConfig
import kr.meeor.mcstreamapi.authserver.config.HttpConfig
import kr.meeor.mcstreamapi.authserver.config.PlatformConfig
import kr.meeor.mcstreamapi.authserver.config.PlatformOAuthConfig
import kr.meeor.mcstreamapi.authserver.config.SecurityConfig
import kr.meeor.mcstreamapi.authserver.config.ServerConfig
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.pairing.ChannelInfo
import kr.meeor.mcstreamapi.authserver.pairing.InMemoryPairingStore
import kr.meeor.mcstreamapi.authserver.pairing.OAuthToken
import kr.meeor.mcstreamapi.authserver.pairing.PairingService
import kr.meeor.mcstreamapi.authserver.security.SharedSecretValidator

class PairingRoutesTest {
    @Test
    fun `invalid shared secret response does not expose provided value`() = testApplication {
        val secret = "mca_test_secret_abcdefghijklmnopqrstuvwxyz"

        application {
            this.install(ContentNegotiation) {
                json()
            }
            routing {
                pairingRoutes(
                    validatedConfig(secret),
                    PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600),
                    SharedSecretValidator(secret),
                )
            }
        }

        val providedSecret = "wrong-secret-that-must-not-echo"
        val response = client.get("/api/pairing/TEST01") {
            header("X-McStreamApi-Secret", providedSecret)
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(body.contains("INVALID_SHARED_SECRET"))
        assertFalse(body.contains(providedSecret))
    }

    @Test
    fun `authorized token is exposed only once through pairing api`() = testApplication {
        val secret = "mca_test_secret_abcdefghijklmnopqrstuvwxyz"
        val pairingService = PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600)
        val pending = pairingService.createPending(
            platform = "chzzk",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerName = "Meeor",
            pairingCode = "TEST01",
        )
        pairingService.authorize(pending.pairingCode, channelInfo(), token())

        application {
            this.install(ContentNegotiation) {
                json()
            }
            routing {
                pairingRoutes(
                    validatedConfig(secret),
                    pairingService,
                    SharedSecretValidator(secret),
                )
            }
        }

        val first = client.get("/api/pairing/TEST01") {
            header("X-McStreamApi-Secret", secret)
        }
        val firstBody = first.bodyAsText()
        val second = client.get("/api/pairing/TEST01") {
            header("X-McStreamApi-Secret", secret)
        }
        val secondBody = second.bodyAsText()

        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(firstBody.contains("access-token"))
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertTrue(secondBody.contains("PAIRING_CONSUMED"))
        assertFalse(secondBody.contains("access-token"))
        assertFalse(secondBody.contains("refresh-token"))
    }

    private fun validatedConfig(secret: String): ValidatedConfig =
        ValidatedConfig(
            config = AppConfig(
                server = ServerConfig(
                    host = "127.0.0.1",
                    port = 18084,
                    publicBaseUrl = "https://auth.example.com/mca",
                    allowInsecureLocalhost = false,
                ),
                security = SecurityConfig(
                    sharedSecret = secret,
                    pairingExpireSeconds = 600,
                    stateExpireSeconds = 600,
                    enableRateLimit = true,
                    trustedProxyHeaders = true,
                ),
                http = HttpConfig(
                    requestTimeoutSeconds = 15,
                    shutdownTimeoutSeconds = 10,
                ),
                cleanup = CleanupConfig(
                    intervalSeconds = 60,
                    expiredSessionRetainSeconds = 300,
                    consumedSessionRetainSeconds = 60,
                    failedSessionRetainSeconds = 600,
                ),
                platforms = mapOf(
                    "chzzk" to PlatformConfig(
                        enabled = true,
                        clientId = "client-id",
                        clientSecret = "client-secret",
                        redirectUri = "https://auth.example.com/mca/oauth/chzzk/callback",
                        scopes = listOf("user:read"),
                        oauth = PlatformOAuthConfig(
                            authorizeEndpoint = "https://provider.example/authorize",
                            tokenEndpoint = "https://provider.example/token",
                            refreshEndpoint = "https://provider.example/token",
                            channelInfoEndpoint = "https://provider.example/channel",
                        ),
                    ),
                ),
            ),
            enabledPlatforms = setOf("chzzk"),
            disabledPlatforms = emptyMap(),
        )

    private fun token(): OAuthToken =
        OAuthToken(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "Bearer",
            scopes = setOf("donation.read"),
            expiresAt = Instant.parse("2026-06-10T01:00:00Z"),
        )

    private fun channelInfo(): ChannelInfo =
        ChannelInfo(
            platform = "chzzk",
            channelId = "channel-id",
            channelName = "channel-name",
        )
}
