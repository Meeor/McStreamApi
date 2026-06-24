package kr.meeor.mcstreamapi.command

import kr.meeor.mcstreamapi.auth.PairingConnector
import kr.meeor.mcstreamapi.auth.PairingStartResult
import kr.meeor.mcstreamapi.config.PluginRuntimeState
import kr.meeor.mcstreamapi.reward.ManualRewardApplier
import kr.meeor.mcstreamapi.reward.ManualRewardApplyResult
import kr.meeor.mcstreamapi.session.ActiveDonationSession
import kr.meeor.mcstreamapi.token.ConnectedTokenInfo
import java.time.Instant

class McaCommandService(
    private val runtimeStateProvider: () -> PluginRuntimeState?,
    private val reloadRuntimeState: () -> PluginRuntimeState,
    private val pairingConnector: PairingConnector? = null,
    private val manualRewardApplier: ManualRewardApplier? = null,
    private val connectedPlayerNames: () -> List<String> = { emptyList() },
    private val connectedTokens: () -> List<ConnectedTokenInfo> = { emptyList() },
    private val activeSessions: () -> List<ActiveDonationSession> = { emptyList() },
    private val streamerPlayers: () -> List<StreamerPlayerIdentity> = { emptyList() },
) {
    fun execute(sender: McaCommandSender, args: List<String>): McaCommandResult {
        if (args.isEmpty()) {
            return McaCommandResult(HELP_MESSAGE)
        }

        return when (args[0].lowercase()) {
            "connect" -> connect(sender, args.drop(1))
            "reload" -> reload(sender)
            "apply" -> apply(sender, args.drop(1))
            "apply-streamer" -> applyStreamer(sender, args.drop(1))
            "status" -> status(sender, args.drop(1))
            else -> McaCommandResult("알 수 없는 명령입니다. /mca")
        }
    }

    fun complete(sender: McaCommandSender, args: List<String>): List<String> {
        return when (args.size) {
            0, 1 -> subcommandsFor(sender).matching(args.firstOrNull())
            2 -> when (args[0].lowercase()) {
                "connect" -> SUPPORTED_PLATFORMS.matching(args[1])
                "apply" -> if (sender.canUse(PERMISSION_APPLY)) {
                    connectedPlayerNames().matching(args[1])
                } else {
                    emptyList()
                }
                "apply-streamer" -> if (sender.canUse(PERMISSION_APPLY)) {
                    streamerPlayers().map(StreamerPlayerIdentity::displayName).distinct().matching(args[1])
                } else {
                    emptyList()
                }
                "status" -> if (sender.canUse(PERMISSION_STATUS)) {
                    connectedPlayerNames().matching(args[1])
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "apply" -> if (sender.canUse(PERMISSION_APPLY)) {
                    manualRewardApplier?.amountSuggestions().orEmpty().matching(args[2])
                } else {
                    emptyList()
                }
                "apply-streamer" -> if (sender.canUse(PERMISSION_APPLY)) {
                    streamerPlayers().firstOrNull { it.matches(args[1]) }
                        ?.let { manualRewardApplier?.streamerAmountSuggestions(it.uuid).orEmpty() }
                        .orEmpty()
                        .matching(args[2])
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "apply" -> if (sender.canUse(PERMISSION_APPLY)) {
                    SUPPORTED_PLATFORMS.matching(args[3])
                } else {
                    emptyList()
                }
                "apply-streamer" -> if (sender.canUse(PERMISSION_APPLY)) {
                    streamerPlayers().firstOrNull { it.matches(args[1]) }
                        ?.platforms.orEmpty().sorted().matching(args[3])
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun connect(sender: McaCommandSender, args: List<String>): McaCommandResult {
        if (sender.type != SenderType.PLAYER) {
            return McaCommandResult("이 명령은 플레이어만 사용할 수 있습니다.")
        }

        val platform = args.firstOrNull()?.lowercase()
            ?: return McaCommandResult("사용법: /mca connect <chzzk|soop>")

        if (platform !in SUPPORTED_PLATFORMS) {
            return McaCommandResult("지원하지 않는 플랫폼입니다. 사용 가능: chzzk, soop")
        }

        val runtimeState = runtimeStateProvider()
            ?: return McaCommandResult("플러그인 설정이 아직 준비되지 않았습니다.")

        if (!runtimeState.validation.authAvailable) {
            return McaCommandResult("AuthServer 설정이 올바르지 않아 연결할 수 없습니다.")
        }

        if (platform !in runtimeState.validation.enabledPlatforms) {
            return McaCommandResult("${platform} 설정이 비활성화되어 있습니다.")
        }

        val authConfig = runtimeState.validation.authConfig
            ?: return McaCommandResult("AuthServer 설정이 올바르지 않아 연결할 수 없습니다.")
        val connector = pairingConnector
            ?: return McaCommandResult("인증 연결 기능이 아직 준비되지 않았습니다.")

        return when (val result = connector.start(sender, platform, authConfig)) {
            is PairingStartResult.Started -> McaCommandResult(
                message = connectStartedMessage(platform, result.pairingCode),
                clickUrl = result.authorizeUrl,
                consoleLog = "인증 요청 생성: player=${sender.name} platform=$platform pairingCode=${result.pairingCode}",
            )
            is PairingStartResult.Failure -> McaCommandResult(result.message)
        }
    }

    private fun connectStartedMessage(platform: String, pairingCode: String): String {
        return if (platform == "soop") {
            listOf(
                "인증을 진행하려면 [연결하러 가기]를 클릭하세요.",
                "SOOP 자동 인증이 실패해 사이트에서 코드를 요구하면 다음 코드를 입력하세요: $pairingCode",
            ).joinToString("\n")
        } else {
            "인증을 진행하려면 [연결하러 가기]를 클릭하세요."
        }
    }

    private fun reload(sender: McaCommandSender): McaCommandResult {
        if (!sender.canUse(PERMISSION_RELOAD)) {
            return McaCommandResult("권한이 없습니다.")
        }

        val runtimeState = reloadRuntimeState()
        val enabled = runtimeState.validation.enabledPlatforms.sorted().joinToString(",").ifBlank { "none" }

        return if (runtimeState.runtimeAvailable) {
            McaCommandResult("설정을 다시 불러왔습니다. 활성 플랫폼: $enabled")
        } else {
            McaCommandResult("설정을 다시 불러왔지만 런타임 기능은 비활성화 상태입니다. 활성 플랫폼: $enabled")
        }
    }

    private fun apply(sender: McaCommandSender, args: List<String>): McaCommandResult {
        if (!sender.canUse(PERMISSION_APPLY)) {
            return McaCommandResult("권한이 없습니다.")
        }

        if (args.size < 2) {
            return McaCommandResult("사용법: /mca apply <player> <amount> [chzzk|soop]")
        }

        val amount = args[1].toLongOrNull()
        if (amount == null || amount <= 0) {
            return McaCommandResult("amount는 1 이상의 숫자여야 합니다.")
        }

        val platform = resolveApplyPlatform(args[0], args.getOrNull(2))
            ?: return McaCommandResult("연결된 플랫폼을 확인할 수 없습니다. 테스트 적용은 /mca apply <player> <amount> <chzzk|soop> 형식으로 플랫폼을 지정해주세요.")
        if (platform == APPLY_PLATFORM_AMBIGUOUS) {
            return McaCommandResult("여러 플랫폼이 연결되어 있습니다. 사용법: /mca apply <player> <amount> <chzzk|soop>")
        }
        if (platform == APPLY_PLATFORM_UNSUPPORTED) {
            return McaCommandResult("지원하지 않는 플랫폼입니다. 사용 가능: chzzk, soop")
        }
        val applier = manualRewardApplier
            ?: return McaCommandResult("수동 보상 적용 기능이 아직 준비되지 않았습니다.")

        return when (val result = applier.apply(args[0], amount, platform)) {
            is ManualRewardApplyResult.Success -> McaCommandResult(
                listOf(
                    "§8§m--------------------",
                    "§a§l수동 보상 적용 완료",
                    "§7대상: §f${args[0]}",
                    "§7금액: §f${amount}",
                    "§7플랫폼: §f${result.platform}",
                    "§7보상 ID: §f${result.rewardId}",
                    "§7실행 Action: §f${result.actionCount}개",
                    "§8§m--------------------",
                ).joinToString("\n"),
            )
            is ManualRewardApplyResult.PartialSuccess -> McaCommandResult(
                listOf(
                    "§8§m--------------------",
                    "§e§l수동 보상 일부 적용",
                    "§7대상: §f${args[0]}",
                    "§7금액: §f${amount}",
                    "§7플랫폼: §f${result.platform}",
                    "§7보상 ID: §f${result.rewardId}",
                    "§7실패 Action: §c${result.failedCount}§7/§f${result.actionCount}",
                    "§8§m--------------------",
                ).joinToString("\n"),
            )
            is ManualRewardApplyResult.Failure -> McaCommandResult(
                listOf(
                    "§8§m--------------------",
                    "§c§l수동 보상 적용 실패",
                    "§7대상: §f${args[0]}",
                    "§7금액: §f${amount}",
                    "§7사유: §f${result.message}",
                    "§8§m--------------------",
                ).joinToString("\n"),
            )
        }
    }

    private fun applyStreamer(sender: McaCommandSender, args: List<String>): McaCommandResult {
        if (!sender.canUse(PERMISSION_APPLY)) {
            return McaCommandResult("권한이 없습니다.")
        }
        if (runtimeStateProvider()?.validation?.streamerRewardsEnabled != true) {
            return McaCommandResult("스트리머 전용 보상 기능이 비활성화되어 있습니다.")
        }
        if (args.size < 2) {
            return McaCommandResult("사용법: /mca apply-streamer <player> <amount> [chzzk|soop]")
        }

        val matchedPlayers = streamerPlayers().filter { it.matches(args[0]) }
        if (matchedPlayers.isEmpty()) {
            return McaCommandResult("streamer-rewards.yml에서 플레이어를 찾을 수 없습니다: ${args[0]}")
        }
        if (matchedPlayers.size > 1) {
            return McaCommandResult("같은 이름의 플레이어가 여러 명입니다. 플레이어 UUID를 입력해주세요.")
        }
        val player = matchedPlayers.single()
        val amount = args[1].toLongOrNull()
        if (amount == null || amount <= 0) {
            return McaCommandResult("amount는 1 이상의 숫자여야 합니다.")
        }
        val requestedPlatform = args.getOrNull(2)?.lowercase()
        if (requestedPlatform != null && requestedPlatform !in SUPPORTED_PLATFORMS) {
            return McaCommandResult("지원하지 않는 플랫폼입니다. 사용 가능: chzzk, soop")
        }
        val platform = requestedPlatform ?: when (player.platforms.size) {
            0 -> return McaCommandResult("플레이어의 전용 보상 플랫폼이 없습니다.")
            1 -> player.platforms.single()
            else -> return McaCommandResult(
                "여러 플랫폼이 설정되어 있습니다. /mca apply-streamer <player> <amount> <chzzk|soop> 형식으로 지정해주세요.",
            )
        }
        if (platform !in player.platforms) {
            return McaCommandResult("플레이어의 전용 보상에 $platform 플랫폼이 없습니다.")
        }
        val applier = manualRewardApplier
            ?: return McaCommandResult("수동 보상 적용 기능이 아직 준비되지 않았습니다.")

        return when (val result = applier.applyStreamer(player.displayName, player.uuid, amount, platform)) {
            is ManualRewardApplyResult.Success -> McaCommandResult(
                listOf(
                    "§8§m--------------------",
                    "§a§l스트리머 전용 보상 적용 완료",
                    "§7대상: §f${player.displayName}",
                    "§7UUID: §f${player.uuid}",
                    "§7금액: §f$amount",
                    "§7플랫폼: §f${result.platform}",
                    "§7보상 ID: §f${result.rewardId}",
                    "§7실행 Action: §f${result.actionCount}개",
                    "§8§m--------------------",
                ).joinToString("\n"),
            )
            is ManualRewardApplyResult.PartialSuccess -> McaCommandResult(
                "§e스트리머 전용 보상 일부 적용: reward=${result.rewardId} 실패=${result.failedCount}/${result.actionCount}",
            )
            is ManualRewardApplyResult.Failure -> McaCommandResult(
                "§c스트리머 전용 보상 적용 실패: ${result.message}",
            )
        }
    }

    private fun status(sender: McaCommandSender, args: List<String>): McaCommandResult {
        if (!sender.canUse(PERMISSION_STATUS)) {
            return McaCommandResult("권한이 없습니다.")
        }

        val runtimeState = runtimeStateProvider()
            ?: return McaCommandResult(
                listOf(
                    "§8§m--------------------",
                    "§b§lMcStreamApi 상태",
                    "§7설정: §c미초기화",
                    "§8§m--------------------",
                ).joinToString("\n"),
            )
        val playerQuery = args.firstOrNull()
        if (!playerQuery.isNullOrBlank()) {
            return playerStatus(playerQuery)
        }

        val enabledPlatforms = runtimeState.validation.enabledPlatforms.sorted()
        val disabledPlatforms = runtimeState.validation.disabledPlatforms.sorted()
        val warnings = runtimeState.validation.warnings.size
        val connectedPlayerCount = connectedPlayerNames().size

        return McaCommandResult(
            listOf(
                "§8§m--------------------",
                "§b§lMcStreamApi 상태",
                "§7런타임: ${runtimeState.runtimeAvailable.coloredEnabled()}",
                "§7AuthServer: ${runtimeState.validation.authAvailable.coloredEnabled()}",
                "§7활성 플랫폼: §f${enabledPlatforms.joinToString(", ").ifBlank { "없음" }}",
                "§7비활성 플랫폼: §8${disabledPlatforms.joinToString(", ").ifBlank { "없음" }}",
                "§7연결 플레이어: §f${connectedPlayerCount}명",
                "§7설정 경고: ${if (warnings == 0) "§a없음" else "§e${warnings}개"}",
                "§8/mca status <player> §7로 토큰 상태 확인",
                "§8§m--------------------",
            ).joinToString("\n"),
        )
    }

    private fun playerStatus(playerQuery: String): McaCommandResult {
        val tokens = connectedTokens()
        val matched = tokens.filter { token ->
            token.minecraftPlayerName.equals(playerQuery, ignoreCase = true) ||
                token.minecraftUuid.equals(playerQuery, ignoreCase = true)
        }

        if (matched.isEmpty()) {
            return McaCommandResult(
                listOf(
                    "§8§m--------------------",
                    "§b§lMcStreamApi 플레이어 상태",
                    "§7대상: §f$playerQuery",
                    "§c저장된 연결 토큰이 없습니다.",
                    "§8§m--------------------",
                ).joinToString("\n"),
            )
        }

        val active = activeSessions().map { it.playerUuid to it.platform }.toSet()
        val playerName = matched.firstNotNullOfOrNull { it.minecraftPlayerName } ?: playerQuery
        val lines = mutableListOf(
            "§8§m--------------------",
            "§b§lMcStreamApi 플레이어 상태",
            "§7대상: §f$playerName",
        )
        matched.sortedBy { it.platform }.forEach { token ->
            val sessionConnected = token.minecraftUuid to token.platform in active
            lines.add("§8- §f${token.platform}")
            lines.add("  §7토큰: ${token.tokenStatusText()}")
            lines.add("  §7세션: ${if (sessionConnected) "§a연결됨" else "§c연결 안 됨"}")
            lines.add("  §7채널: §f${token.channelName ?: token.channelId ?: "없음"}")
        }
        lines.add("§8§m--------------------")

        return McaCommandResult(lines.joinToString("\n"))
    }

    private fun resolveApplyPlatform(playerQuery: String, requestedPlatform: String?): String? {
        val normalizedRequested = requestedPlatform?.lowercase()
        if (normalizedRequested != null && normalizedRequested !in SUPPORTED_PLATFORMS) {
            return APPLY_PLATFORM_UNSUPPORTED
        }
        if (normalizedRequested != null) {
            return normalizedRequested
        }

        val platforms = connectedPlatformsFor(playerQuery)
        return when (platforms.size) {
            0 -> null
            1 -> platforms.single()
            else -> APPLY_PLATFORM_AMBIGUOUS
        }
    }

    private fun connectedPlatformsFor(playerQuery: String): List<String> {
        return connectedTokens()
            .filter { token ->
                token.minecraftPlayerName.equals(playerQuery, ignoreCase = true) ||
                    token.minecraftUuid.equals(playerQuery, ignoreCase = true)
            }
            .map { it.platform }
            .distinct()
            .sorted()
    }

    private fun subcommandsFor(sender: McaCommandSender): List<String> {
        val commands = mutableListOf("connect")
        if (sender.canUse(PERMISSION_RELOAD)) {
            commands.add("reload")
        }
        if (sender.canUse(PERMISSION_APPLY)) {
            commands.add("apply")
            commands.add("apply-streamer")
        }
        if (sender.canUse(PERMISSION_STATUS)) {
            commands.add("status")
        }
        return commands
    }

    private fun List<String>.matching(prefix: String?): List<String> {
        val normalized = prefix.orEmpty().lowercase()
        return filter { it.lowercase().startsWith(normalized) }
    }

    private fun Boolean.coloredEnabled(): String {
        return if (this) "§a활성" else "§c비활성"
    }

    private fun ConnectedTokenInfo.tokenStatusText(): String {
        val seconds = expiresAtEpochSeconds - Instant.now().epochSecond
        return when {
            seconds <= 0 -> "§c만료"
            seconds <= TOKEN_EXPIRING_SOON_SECONDS -> "§e곧 갱신 필요 (${seconds.formatDuration()})"
            else -> "§a정상 (${seconds.formatDuration()})"
        }
    }

    private fun Long.formatDuration(): String {
        val days = this / 86_400
        val hours = (this % 86_400) / 3_600
        val minutes = (this % 3_600) / 60
        return when {
            days > 0 -> "${days}일 ${hours}시간"
            hours > 0 -> "${hours}시간 ${minutes}분"
            else -> "${minutes.coerceAtLeast(0)}분"
        }
    }

    companion object {
        const val PERMISSION_RELOAD = "mcstreamapi.reload"
        const val PERMISSION_APPLY = "mcstreamapi.apply"
        const val PERMISSION_STATUS = "mcstreamapi.status"

        private val SUPPORTED_PLATFORMS = listOf("chzzk", "soop")
        private const val HELP_MESSAGE = "사용법: /mca connect <platform> | reload | apply <player> <amount> [platform] | apply-streamer <player> <amount> [platform] | status"
        private const val TOKEN_EXPIRING_SOON_SECONDS = 300L
        private const val APPLY_PLATFORM_AMBIGUOUS = "__ambiguous__"
        private const val APPLY_PLATFORM_UNSUPPORTED = "__unsupported__"
    }
}
