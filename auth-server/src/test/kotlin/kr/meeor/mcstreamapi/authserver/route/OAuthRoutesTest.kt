package kr.meeor.mcstreamapi.authserver.route

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kr.meeor.mcstreamapi.authserver.config.AppConfig
import kr.meeor.mcstreamapi.authserver.config.CleanupConfig
import kr.meeor.mcstreamapi.authserver.config.HttpConfig
import kr.meeor.mcstreamapi.authserver.config.PlatformOAuthConfig
import kr.meeor.mcstreamapi.authserver.config.PlatformConfig
import kr.meeor.mcstreamapi.authserver.config.SecurityConfig
import kr.meeor.mcstreamapi.authserver.config.ServerConfig
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.oauth.InMemoryStateStore
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProvider
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProviderRegistry
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateService
import kr.meeor.mcstreamapi.authserver.oauth.StateIdGenerator
import kr.meeor.mcstreamapi.authserver.pairing.ChannelInfo
import kr.meeor.mcstreamapi.authserver.pairing.InMemoryPairingStore
import kr.meeor.mcstreamapi.authserver.pairing.OAuthToken
import kr.meeor.mcstreamapi.authserver.pairing.PairingService
import kr.meeor.mcstreamapi.authserver.pairing.PairingStatus
import java.time.Instant
import java.util.UUID

class OAuthRoutesTest {
    @Test
    fun `callback success authorizes pairing and token is consumed once`() = testApplication {
        val validatedConfig = validatedConfig("soop")
        val pairingService = PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600)
        val stateService = OAuthStateService(
            store = InMemoryStateStore(),
            stateExpireSeconds = 600,
            stateIdGenerator = FixedStateIdGenerator(),
        )
        val providerRegistry = OAuthProviderRegistry(listOf(FakeOAuthProvider("soop", supportsState = false)))

        pairingService.createPending(
            platform = "soop",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerName = "Meeor",
            pairingCode = "A7K29Q",
        )

        application {
            routing {
                oauthRoutes(validatedConfig, pairingService, stateService, providerRegistry)
            }
        }

        val nonRedirectingClient = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        val start = nonRedirectingClient.get("/oauth/soop/start?pairingCode=A7K29Q")
        assertEquals(HttpStatusCode.Found, start.status)
        assertEquals("https://provider.example/authorize?platform=soop", start.headers["Location"])

        val callback = nonRedirectingClient.get("/oauth/soop/callback?code=ok")
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals(true, callback.bodyAsText().contains("인증이 완료되었습니다"))

        val firstPoll = pairingService.poll("A7K29Q")
        val secondPoll = pairingService.poll("A7K29Q")

        assertEquals(PairingStatus.AUTHORIZED, firstPoll.status)
        assertNotNull(firstPoll.token)
        assertEquals(PairingStatus.CONSUMED, secondPoll.status)
    }

    @Test
    fun `soop callback cookie allows concurrent pending requests`() = testApplication {
        val validatedConfig = validatedConfig("soop")
        val pairingService = PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600)
        val stateService = OAuthStateService(
            store = InMemoryStateStore(),
            stateExpireSeconds = 600,
            stateIdGenerator = FixedStateIdGenerator(),
        )
        val providerRegistry = OAuthProviderRegistry(listOf(FakeOAuthProvider("soop", supportsState = false)))

        pairingService.createPending(
            platform = "soop",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerName = "Meeor",
            pairingCode = "A7K29Q",
        )
        pairingService.createPending(
            platform = "soop",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174001"),
            playerName = "Steve",
            pairingCode = "B7K29Q",
        )

        application {
            routing {
                oauthRoutes(validatedConfig, pairingService, stateService, providerRegistry)
            }
        }

        val firstBrowser = createClient {
            followRedirects = false
            install(HttpCookies)
        }
        val secondBrowser = createClient {
            followRedirects = false
            install(HttpCookies)
        }

        assertEquals(HttpStatusCode.Found, firstBrowser.get("/oauth/soop/start?pairingCode=A7K29Q").status)
        assertEquals(HttpStatusCode.Found, secondBrowser.get("/oauth/soop/start?pairingCode=B7K29Q").status)

        assertEquals(HttpStatusCode.OK, secondBrowser.get("/oauth/soop/callback?code=ok").status)
        assertEquals(HttpStatusCode.OK, firstBrowser.get("/oauth/soop/callback?code=ok").status)

        assertEquals(PairingStatus.AUTHORIZED, pairingService.poll("A7K29Q").status)
        assertEquals(PairingStatus.AUTHORIZED, pairingService.poll("B7K29Q").status)
    }

    @Test
    fun `soop callback without cookie can complete with pairing code fallback`() = testApplication {
        val validatedConfig = validatedConfig("soop")
        val pairingService = PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600)
        val stateService = OAuthStateService(
            store = InMemoryStateStore(),
            stateExpireSeconds = 600,
            stateIdGenerator = FixedStateIdGenerator(),
        )
        val providerRegistry = OAuthProviderRegistry(listOf(FakeOAuthProvider("soop", supportsState = false)))

        pairingService.createPending(
            platform = "soop",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerName = "Meeor",
            pairingCode = "A7K29Q",
        )

        application {
            routing {
                oauthRoutes(validatedConfig, pairingService, stateService, providerRegistry)
            }
        }

        val nonRedirectingClient = createClient {
            followRedirects = false
        }

        assertEquals(HttpStatusCode.Found, nonRedirectingClient.get("/oauth/soop/start?pairingCode=A7K29Q").status)

        val fallback = client.get("/oauth/soop/callback?code=ok")
        assertEquals(HttpStatusCode.OK, fallback.status)
        assertEquals(true, fallback.bodyAsText().contains("SOOP 자동 인증을 완료할 수 없습니다."))

        val completed = client.post("/oauth/soop/callback") {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("pairingCode", "A7K29Q")
                        append("code", "ok")
                    },
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, completed.status)
        assertEquals(PairingStatus.AUTHORIZED, pairingService.poll("A7K29Q").status)
    }

    @Test
    fun `chzzk callback success authorizes pairing`() = testApplication {
        val validatedConfig = validatedConfig("chzzk")
        val pairingService = PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600)
        val stateService = OAuthStateService(
            store = InMemoryStateStore(),
            stateExpireSeconds = 600,
            stateIdGenerator = FixedStateIdGenerator(),
        )
        val providerRegistry = OAuthProviderRegistry(listOf(FakeOAuthProvider("chzzk")))

        pairingService.createPending(
            platform = "chzzk",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerName = "Meeor",
            pairingCode = "C7K29Q",
        )

        application {
            routing {
                oauthRoutes(validatedConfig, pairingService, stateService, providerRegistry)
            }
        }

        val nonRedirectingClient = createClient {
            followRedirects = false
        }

        val start = nonRedirectingClient.get("/oauth/chzzk/start?pairingCode=C7K29Q")
        assertEquals(HttpStatusCode.Found, start.status)
        assertEquals("https://provider.example/authorize?platform=chzzk&state=state-1", start.headers["Location"])

        val callback = client.get("/oauth/chzzk/callback?state=state-1&code=ok")
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals(PairingStatus.AUTHORIZED, pairingService.poll("C7K29Q").status)
    }

    @Test
    fun `callback without code fails pairing gracefully`() = testApplication {
        val validatedConfig = validatedConfig("chzzk")
        val pairingService = PairingService(InMemoryPairingStore(), pairingExpireSeconds = 600)
        val stateService = OAuthStateService(
            store = InMemoryStateStore(),
            stateExpireSeconds = 600,
            stateIdGenerator = FixedStateIdGenerator(),
        )
        val providerRegistry = OAuthProviderRegistry(listOf(FakeOAuthProvider("chzzk")))

        pairingService.createPending(
            platform = "chzzk",
            playerUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            playerName = "Meeor",
            pairingCode = "D7K29Q",
        )

        application {
            routing {
                oauthRoutes(validatedConfig, pairingService, stateService, providerRegistry)
            }
        }

        val nonRedirectingClient = createClient {
            followRedirects = false
        }

        nonRedirectingClient.get("/oauth/chzzk/start?pairingCode=D7K29Q")
        val callback = client.get("/oauth/chzzk/callback?state=state-1")

        assertEquals(HttpStatusCode.BadRequest, callback.status)
        assertEquals(true, callback.bodyAsText().contains("OAUTH_CODE_REQUIRED"))
        assertEquals(PairingStatus.FAILED, pairingService.get("D7K29Q").status)
    }

    private fun validatedConfig(platform: String): ValidatedConfig =
        ValidatedConfig(
            config = AppConfig(
                server = ServerConfig(
                    host = "127.0.0.1",
                    port = 18084,
                    publicBaseUrl = "https://auth.example.com/mca",
                    allowInsecureLocalhost = true,
                ),
                security = SecurityConfig(
                    sharedSecret = "mca_abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGH",
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
                    platform to PlatformConfig(
                        enabled = true,
                        clientId = "client-id",
                        clientSecret = "client-secret",
                        redirectUri = "https://auth.example.com/mca/oauth/$platform/callback",
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
            enabledPlatforms = setOf(platform),
            disabledPlatforms = emptyMap(),
        )

    private class FixedStateIdGenerator : StateIdGenerator {
        private var next = 0

        override fun generate(): String {
            next++
            return "state-$next"
        }
    }

    private class FakeOAuthProvider(
        override val platform: String,
        override val supportsState: Boolean = true,
    ) : OAuthProvider {

        override fun buildAuthorizeUrl(state: String): String =
            if (supportsState) {
                "https://provider.example/authorize?platform=$platform&state=$state"
            } else {
                "https://provider.example/authorize?platform=$platform"
            }

        override suspend fun exchangeCodeForToken(code: String, state: String): OAuthToken =
            OAuthToken(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                tokenType = "Bearer",
                scopes = setOf("donation.read"),
                expiresAt = Instant.parse("2026-06-10T01:00:00Z"),
            )

        override suspend fun fetchChannelInfo(accessToken: String): ChannelInfo =
            ChannelInfo(
                platform = platform,
                channelId = "channel-id",
                channelName = "channel-name",
            )
    }
}
