package kr.meeor.mcstreamapi.command

import kr.meeor.mcstreamapi.config.ConfigValidationResult
import kr.meeor.mcstreamapi.config.PluginAuthConfig
import kr.meeor.mcstreamapi.config.PluginRuntimeState
import kr.meeor.mcstreamapi.session.ActiveDonationSession
import kr.meeor.mcstreamapi.token.ConnectedTokenInfo
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McaCommandServiceTest {
    @Test
    fun `connect is available to players without permission`() {
        val service = service(runtimeState(enabledPlatforms = setOf("chzzk")))

        val result = service.execute(player(), listOf("connect", "chzzk"))

        assertEquals("인증 연결 기능이 아직 준비되지 않았습니다.", result.message)
    }

    @Test
    fun `connect rejects console sender`() {
        val service = service(runtimeState(enabledPlatforms = setOf("chzzk")))

        val result = service.execute(console(allPermissions = true), listOf("connect", "chzzk"))

        assertEquals("이 명령은 플레이어만 사용할 수 있습니다.", result.message)
    }

    @Test
    fun `management commands require permissions`() {
        val service = service(runtimeState(enabledPlatforms = setOf("chzzk")))

        assertEquals("권한이 없습니다.", service.execute(player(), listOf("reload")).message)
        assertEquals("권한이 없습니다.", service.execute(player(), listOf("apply", "Steve", "1000")).message)
        assertEquals("권한이 없습니다.", service.execute(player(), listOf("apply-streamer", "Steve", "1000")).message)
        assertEquals("권한이 없습니다.", service.execute(player(), listOf("status")).message)
    }

    @Test
    fun `reload is allowed from console with permission`() {
        val service = service(runtimeState(enabledPlatforms = setOf("chzzk")))

        val result = service.execute(console(allPermissions = true), listOf("reload"))

        assertContains(result.message, "설정을 다시 불러왔습니다.")
    }

    @Test
    fun `management commands are allowed from console without permission`() {
        val service = service(runtimeState(enabledPlatforms = setOf("chzzk")))

        val result = service.execute(console(allPermissions = false), listOf("reload"))

        assertContains(result.message, "설정을 다시 불러왔습니다.")
    }

    @Test
    fun `tab completion hides management commands without permissions`() {
        val service = service(runtimeState(enabledPlatforms = setOf("chzzk")))

        assertEquals(listOf("connect"), service.complete(player(), listOf("")))
        assertEquals(
            listOf("connect", "reload", "apply", "apply-streamer", "status"),
            service.complete(player(allPermissions = true), listOf("")),
        )
    }

    @Test
    fun `apply player completion uses connected players and permission`() {
        val service = McaCommandService(
            runtimeStateProvider = { runtimeState(enabledPlatforms = setOf("chzzk")) },
            reloadRuntimeState = { runtimeState(enabledPlatforms = setOf("chzzk")) },
            connectedPlayerNames = { listOf("Meeor", "Steve") },
        )

        assertEquals(emptyList(), service.complete(player(), listOf("apply", "")))
        assertEquals(listOf("Meeor"), service.complete(player(allPermissions = true), listOf("apply", "M")))
    }

    @Test
    fun `apply streamer player completion uses resolved streamer names`() {
        val state = runtimeState(enabledPlatforms = setOf("soop"), streamerRewardsEnabled = true)
        val service = McaCommandService(
            runtimeStateProvider = { state },
            reloadRuntimeState = { state },
            streamerPlayers = {
                listOf(
                    StreamerPlayerIdentity(
                        uuid = "00000000-0000-0000-0000-000000000001",
                        playerName = "Meeor",
                        platforms = setOf("soop"),
                    ),
                )
            },
        )

        assertEquals(
            listOf("Meeor"),
            service.complete(player(allPermissions = true), listOf("apply-streamer", "M")),
        )
        assertEquals(
            listOf("soop"),
            service.complete(player(allPermissions = true), listOf("apply-streamer", "Meeor", "1000", "")),
        )
    }

    @Test
    fun `status player completion uses connected players and permission`() {
        val service = McaCommandService(
            runtimeStateProvider = { runtimeState(enabledPlatforms = setOf("chzzk")) },
            reloadRuntimeState = { runtimeState(enabledPlatforms = setOf("chzzk")) },
            connectedPlayerNames = { listOf("Meeor", "Steve") },
        )

        assertEquals(emptyList(), service.complete(player(), listOf("status", "")))
        assertEquals(listOf("Steve"), service.complete(player(allPermissions = true), listOf("status", "S")))
    }

    @Test
    fun `status player detail excludes sensitive token values`() {
        val service = McaCommandService(
            runtimeStateProvider = { runtimeState(enabledPlatforms = setOf("chzzk", "soop")) },
            reloadRuntimeState = { runtimeState(enabledPlatforms = setOf("chzzk", "soop")) },
            connectedTokens = {
                listOf(
                    ConnectedTokenInfo(
                        platform = "chzzk",
                        minecraftUuid = "uuid",
                        minecraftPlayerName = "Meeor",
                        channelId = "channel-id",
                        channelName = "channel-name",
                        expiresAtEpochSeconds = Instant.now().epochSecond + 3600,
                    ),
                )
            },
            activeSessions = { listOf(ActiveDonationSession(playerUuid = "uuid", playerName = "Meeor", platform = "chzzk")) },
        )

        val message = service.execute(player(allPermissions = true), listOf("status", "Meeor")).message

        assertContains(message, "chzzk")
        assertContains(message, "토큰:")
        assertContains(message, "세션:")
        assertContains(message, "연결됨")
        assertFalse(message.contains("access", ignoreCase = true))
        assertFalse(message.contains("refresh", ignoreCase = true))
    }

    private fun service(runtimeState: PluginRuntimeState): McaCommandService {
        return McaCommandService(
            runtimeStateProvider = { runtimeState },
            reloadRuntimeState = { runtimeState },
        )
    }

    private fun runtimeState(
        enabledPlatforms: Set<String>,
        streamerRewardsEnabled: Boolean = false,
    ): PluginRuntimeState {
        return PluginRuntimeState(
            createdFiles = emptyList(),
            validation = ConfigValidationResult(
                authAvailable = true,
                enabledPlatforms = enabledPlatforms,
                disabledPlatforms = setOf("chzzk", "soop") - enabledPlatforms,
                warnings = emptyList(),
                streamerRewardsEnabled = streamerRewardsEnabled,
                authConfig = PluginAuthConfig(
                    serverBaseUrl = "http://localhost:8080",
                    sharedSecret = "12345678901234567890123456789012",
                    pollingIntervalSeconds = 3,
                    pairingTimeoutSeconds = 600,
                ),
            ),
        )
    }

    private fun player(allPermissions: Boolean = false): McaCommandSender {
        return McaCommandSender(
            name = "Steve",
            uuid = "00000000-0000-0000-0000-000000000001",
            type = SenderType.PLAYER,
            hasPermission = { allPermissions },
        )
    }

    private fun console(allPermissions: Boolean): McaCommandSender {
        return McaCommandSender(
            name = "CONSOLE",
            uuid = null,
            type = SenderType.CONSOLE,
            hasPermission = { allPermissions },
        )
    }
}
