package kr.meeor.mcstreamapi.auth

import kr.meeor.mcstreamapi.command.McaCommandSender
import kr.meeor.mcstreamapi.command.SenderType
import kr.meeor.mcstreamapi.config.PluginAuthConfig
import kr.meeor.mcstreamapi.token.TokenStore
import java.nio.file.Files
import kotlin.io.path.writeText
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PairingConnectorTest {
    @Test
    fun `starts pairing and stores token when authorized`() {
        val root = Files.createTempDirectory("mcstreamapi-pairing")
        val authClient = FakeAuthClient(
            statusResult = PairingStatusResult(
                pairingCode = "ABCDEFGH",
                status = "AUTHORIZED",
                platform = "chzzk",
                minecraftUuid = PLAYER_UUID,
                channelId = "channel-id",
                channelName = "channel-name",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                tokenType = "Bearer",
                scopes = listOf("user:read"),
                expiresAt = Instant.ofEpochSecond(1234),
            ),
        )
        val scheduler = FakePairingScheduler()
        val store = TokenStore(root.resolve("tokens"), root.resolve("secret.key"))
        val connector = PairingConnector(
            authClientFactory = { authClient },
            tokenStore = store,
            scheduler = scheduler,
            codeGenerator = FixedCodeGenerator("ABCDEFGH"),
        )
        val sender = playerSender()

        val start = connector.start(sender, "chzzk", authConfig())
        scheduler.runOnce()

        assertIs<PairingStartResult.Started>(start)
        assertContains(start.authorizeUrl, "/oauth/chzzk/start")
        assertEquals("ABCDEFGH", authClient.registered?.pairingCode)
        assertEquals("access-token", store.load("chzzk", PLAYER_UUID).getOrThrow()?.accessToken)
        assertEquals(listOf("chzzk 인증이 완료되었습니다."), senderMessages)
    }

    @Test
    fun `rejects authorized response for different uuid`() {
        val root = Files.createTempDirectory("mcstreamapi-pairing")
        val authClient = FakeAuthClient(
            statusResult = PairingStatusResult(
                pairingCode = "ABCDEFGH",
                status = "AUTHORIZED",
                platform = "chzzk",
                minecraftUuid = "00000000-0000-0000-0000-000000000999",
                channelId = "channel-id",
                channelName = "channel-name",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                tokenType = "Bearer",
                scopes = emptyList(),
                expiresAt = Instant.ofEpochSecond(1234),
            ),
        )
        val scheduler = FakePairingScheduler()
        val connector = PairingConnector(
            authClientFactory = { authClient },
            tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
            scheduler = scheduler,
            codeGenerator = FixedCodeGenerator("ABCDEFGH"),
        )

        connector.start(playerSender(), "chzzk", authConfig())
        scheduler.runOnce()

        assertEquals(listOf("인증 응답의 플레이어 UUID가 일치하지 않습니다."), senderMessages)
    }

    @Test
    fun `maps shared secret failure to user message`() {
        val connector = PairingConnector(
            authClientFactory = { FakeAuthClient(registerFailure = AuthClientException("INVALID_SHARED_SECRET")) },
            tokenStore = TokenStore(
                Files.createTempDirectory("mcstreamapi-pairing").resolve("tokens"),
                Files.createTempDirectory("mcstreamapi-pairing").resolve("secret.key"),
            ),
            scheduler = FakePairingScheduler(),
            codeGenerator = FixedCodeGenerator("ABCDEFGH"),
        )

        val result = connector.start(playerSender(), "chzzk", authConfig())

        assertEquals("AuthServer sharedSecret이 일치하지 않습니다.", (result as PairingStartResult.Failure).message)
    }

    @Test
    fun `maps unreachable auth server failure to user message`() {
        val connector = PairingConnector(
            authClientFactory = { FakeAuthClient(registerFailure = AuthClientException("AUTH_SERVER_UNREACHABLE")) },
            tokenStore = TokenStore(
                Files.createTempDirectory("mcstreamapi-pairing").resolve("tokens"),
                Files.createTempDirectory("mcstreamapi-pairing").resolve("secret.key"),
            ),
            scheduler = FakePairingScheduler(),
            codeGenerator = FixedCodeGenerator("ABCDEFGH"),
        )

        val result = connector.start(playerSender(), "chzzk", authConfig())

        assertEquals("AuthServer에 연결할 수 없습니다.", (result as PairingStartResult.Failure).message)
    }

    @Test
    fun `notifies pairing expiration`() {
        val root = Files.createTempDirectory("mcstreamapi-pairing")
        val authClient = FakeAuthClient(pollFailure = AuthClientException("PAIRING_EXPIRED"))
        val scheduler = FakePairingScheduler()
        val connector = PairingConnector(
            authClientFactory = { authClient },
            tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
            scheduler = scheduler,
            codeGenerator = FixedCodeGenerator("ABCDEFGH"),
        )

        connector.start(playerSender(), "chzzk", authConfig())
        scheduler.runOnce()

        assertEquals(listOf("인증 시간이 만료되었습니다. 다시 시도해 주세요."), senderMessages)
    }

    @Test
    fun `notifies token save failure without crashing polling task`() {
        val root = Files.createTempDirectory("mcstreamapi-pairing")
        root.resolve("secret.key").writeText("not-base64")
        val authClient = FakeAuthClient(
            statusResult = PairingStatusResult(
                pairingCode = "ABCDEFGH",
                status = "AUTHORIZED",
                platform = "chzzk",
                minecraftUuid = PLAYER_UUID,
                channelId = "channel-id",
                channelName = "channel-name",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                tokenType = "Bearer",
                scopes = emptyList(),
                expiresAt = Instant.ofEpochSecond(1234),
            ),
        )
        val scheduler = FakePairingScheduler()
        val connector = PairingConnector(
            authClientFactory = { authClient },
            tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
            scheduler = scheduler,
            codeGenerator = FixedCodeGenerator("ABCDEFGH"),
        )

        connector.start(playerSender(), "chzzk", authConfig())
        scheduler.runOnce()

        assertEquals(listOf("토큰 저장에 실패했습니다. 콘솔 로그를 확인해 주세요."), senderMessages)
    }

    private val senderMessages = mutableListOf<String>()

    private fun playerSender(): McaCommandSender {
        senderMessages.clear()
        return McaCommandSender(
            name = "Steve",
            uuid = PLAYER_UUID,
            type = SenderType.PLAYER,
            hasPermission = { true },
            notify = { senderMessages.add(it) },
        )
    }

    private fun authConfig(): PluginAuthConfig {
        return PluginAuthConfig(
            serverBaseUrl = "http://localhost:8080/mca",
            sharedSecret = "12345678901234567890123456789012",
            pollingIntervalSeconds = 3,
            pairingTimeoutSeconds = 600,
        )
    }

    private class FakeAuthClient(
        private val statusResult: PairingStatusResult? = null,
        private val registerFailure: Throwable? = null,
        private val pollFailure: Throwable? = null,
    ) : AuthClient {
        var registered: PairingRegisterCommand? = null

        override fun registerPairing(request: PairingRegisterCommand): Result<PairingRegisterResult> {
            registered = request
            registerFailure?.let { return Result.failure(it) }
            return Result.success(
                PairingRegisterResult(
                    pairingCode = request.pairingCode,
                    status = "PENDING",
                    expiresInSeconds = 600,
                    authorizeUrl = "http://localhost:8080/mca/oauth/${request.platform}/start?pairingCode=${request.pairingCode}",
                ),
            )
        }

        override fun getPairing(pairingCode: String): Result<PairingStatusResult> {
            pollFailure?.let { return Result.failure(it) }
            return Result.success(assertNotNull(statusResult))
        }
    }

    private class FakePairingScheduler : PairingScheduler {
        private var task: (() -> Unit)? = null

        override fun repeatAsync(
            initialDelayTicks: Long,
            periodTicks: Long,
            task: () -> Unit,
        ): CancellableTask {
            this.task = task
            return CancellableTask { this.task = null }
        }

        fun runOnce() {
            task?.invoke()
        }
    }

    private class FixedCodeGenerator(private val code: String) : PairingCodeGenerator() {
        override fun generate(length: Int): String = code
    }

    private companion object {
        const val PLAYER_UUID = "00000000-0000-0000-0000-000000000001"
    }
}
