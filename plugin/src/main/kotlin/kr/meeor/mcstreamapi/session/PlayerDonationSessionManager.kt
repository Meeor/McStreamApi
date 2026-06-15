package kr.meeor.mcstreamapi.session

import kr.meeor.mcstreamapi.donation.DonationProvider
import kr.meeor.mcstreamapi.donation.DonationProviderSession
import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore

class PlayerDonationSessionManager(
    private val tokenStore: TokenStore,
    private var providers: Map<String, DonationProvider>,
    private val onlinePlayers: OnlinePlayerRegistry,
    private val rewardPipeline: DonationRewardPipeline,
    private val logger: PluginLogger? = null,
    private val deduplicator: DonationEventDeduplicator = DonationEventDeduplicator(),
) {
    private val sessions = mutableMapOf<String, MutableMap<String, SessionRecord>>()

    fun playerJoined(playerUuid: String, playerName: String) {
        logger?.debug("§b[접속] 플레이어 접속 감지: 플레이어=$playerName 후원 세션 확인 시작")
        providers.keys.forEach { platform ->
            startPlatformSession(playerUuid, playerName, platform)
        }
    }

    fun platformAuthenticated(playerUuid: String, playerName: String, platform: String) {
        logger?.debug("§b[인증완료] 후원 세션 연결 준비: 플레이어=$playerName 플랫폼=$platform")
        startPlatformSession(playerUuid, playerName, platform)
    }

    fun playerQuit(playerUuid: String, playerName: String? = null) {
        val playerLabel = playerName ?: sessions[playerUuid]?.values?.firstOrNull()?.playerName ?: playerUuid
        logger?.debug("§b[퇴장] 플레이어 퇴장 감지: 플레이어=$playerLabel 후원 세션 종료 시작")
        val playerSessions = sessions.remove(playerUuid)
        if (playerSessions == null || playerSessions.isEmpty()) {
            logger?.debug("§e[진행] 종료할 후원 세션 없음: 플레이어=$playerLabel")
            return
        }
        playerSessions.forEach { (platform, record) ->
            logger?.debug("§e[진행] 후원 세션 연결 끊는 중: 플레이어=${record.playerName} 플랫폼=$platform")
            runCatching { record.session.stop() }
                .onSuccess { logger?.info("§a[성공] 후원 세션 연결 끊음 완료: 플레이어=${record.playerName} 플랫폼=$platform") }
                .onFailure { throwable ->
                    logger?.warning(
                        "§c[실패] 후원 세션 연결 끊음 실패: 플레이어=${record.playerName} 플랫폼=$platform 원인=${throwable.javaClass.simpleName}",
                    )
                }
        }
    }

    fun stopAll() {
        sessions.keys.toList().forEach(::playerQuit)
    }

    fun replaceProviders(providers: Map<String, DonationProvider>) {
        stopAll()
        this.providers = providers
    }

    fun activeSessions(): List<ActiveDonationSession> {
        return sessions.flatMap { (playerUuid, byPlatform) ->
            byPlatform.map { (platform, record) ->
                ActiveDonationSession(playerUuid = playerUuid, playerName = record.playerName, platform = platform)
            }
        }
    }

    private fun startPlatformSession(playerUuid: String, playerName: String, platform: String) {
        if (!onlinePlayers.isOnline(playerUuid)) {
            logger?.debug("§e[대기] 후원 세션 시작 건너뜀: 플레이어=$playerName 플랫폼=$platform 원인=오프라인")
            return
        }
        if (sessions[playerUuid]?.containsKey(platform) == true) {
            logger?.debug("§e[대기] 후원 세션 이미 연결됨: 플레이어=$playerName 플랫폼=$platform")
            return
        }

        logger?.debug("§e[진행] 후원 세션 토큰 확인 중: 플레이어=$playerName 플랫폼=$platform")
        val token = when (val result = loadTokenWithRetry(platform, playerUuid)) {
            TokenLoadResult.Missing -> {
                logger?.debug("§e[대기] 후원 세션 토큰 없음: 플레이어=$playerName 플랫폼=$platform")
                return
            }
            is TokenLoadResult.Failed -> {
                logger?.warning(
                    "§c[실패] 후원 세션 토큰 읽기 최종 실패: 플레이어=$playerName 플랫폼=$platform 원인=${result.cause.javaClass.simpleName}",
                )
                onlinePlayers.notify(playerUuid, "$platform 연결 토큰을 사용할 수 없습니다. 다시 연결해 주세요.")
                return
            }
            is TokenLoadResult.Loaded -> result.token
        }

        val provider = providers[platform] ?: return
        val channelId = token.channelId ?: token.minecraftUuid
        val channelName = token.channelName ?: playerName

        logger?.debug("§e[진행] 후원 세션 연결 시도: 플레이어=$playerName 플랫폼=$platform 채널=$channelName")
        val session = runCatching {
            provider.startSession(
                minecraftUuid = playerUuid,
                streamerId = channelId,
                streamerName = channelName,
                token = token,
            ) { event ->
                if (!onlinePlayers.isOnline(playerUuid)) {
                    logger?.debug(
                        "§e[대기] 후원 이벤트 무시: 플레이어=$playerName 플랫폼=$platform " +
                            "후원자=${event.donatorName} 수량=${event.amount} 원인=오프라인",
                    )
                    return@startSession
                }
                if (!deduplicator.accept(event)) {
                    logger?.debug(
                        "§e[대기] 후원 이벤트 중복 무시: 플레이어=$playerName 플랫폼=$platform " +
                            "eventId=${event.eventId}",
                    )
                    return@startSession
                }
                logger?.info(
                    "§a[후원] 이벤트 수신: 플레이어=$playerName 플랫폼=$platform " +
                        "후원자=${event.donatorName} 수량=${event.amount}",
                )
                rewardPipeline.handle(playerUuid, event)
            }
        }.getOrElse { throwable ->
            logger?.warning(
                "§c[실패] 후원 세션 연결 실패: 플레이어=$playerName 플랫폼=$platform 원인=${providerErrorMessage(throwable)}",
            )
            onlinePlayers.notify(playerUuid, "$platform 세션을 시작할 수 없습니다. 다시 연결해 주세요.")
            return
        }

        sessions.getOrPut(playerUuid) { mutableMapOf() }[platform] = SessionRecord(playerName = playerName, session = session)
        logger?.debug("§e[진행] 후원 세션 시작 등록 완료: 플레이어=$playerName 플랫폼=$platform 채널=$channelName")
    }

    private fun loadTokenWithRetry(platform: String, playerUuid: String): TokenLoadResult {
        var lastFailure: Throwable? = null
        repeat(MAX_TOKEN_LOAD_ATTEMPTS) { attempt ->
            val token = tokenStore.load(platform, playerUuid).getOrElse { throwable ->
                lastFailure = throwable
                logger?.warning(
                    "§c[실패] 토큰 읽기 실패: 플랫폼=$platform 플레이어UUID=$playerUuid 시도=${attempt + 1} 원인=${throwable.javaClass.simpleName}",
                )
                return@repeat
            }
            if (token != null) {
                return TokenLoadResult.Loaded(token)
            }
        }
        return lastFailure?.let(TokenLoadResult::Failed) ?: TokenLoadResult.Missing
    }

    companion object {
        private const val MAX_TOKEN_LOAD_ATTEMPTS = 3
    }

    private fun providerErrorMessage(throwable: Throwable): String {
        val code = throwable.javaClass.methods
            .firstOrNull { it.name == "getCode" && it.parameterCount == 0 }
            ?.invoke(throwable)
            ?.toString()
        return if (code == null) {
            throwable.javaClass.simpleName
        } else {
            "$code (${providerErrorDescription(code)})"
        }
    }

    private fun providerErrorDescription(code: String): String {
        return when (code) {
            "CHZZK_INVALID_TOKEN" -> "토큰이 만료되었거나 유효하지 않습니다"
            "CHZZK_SCOPE_DENIED" -> "CHZZK 앱 권한(scope)이 부족합니다"
            "CHZZK_RATE_LIMITED" -> "CHZZK API 호출 제한에 걸렸습니다"
            "CHZZK_SESSION_URL_MISSING" -> "CHZZK 세션 URL 응답이 비어 있습니다"
            "CHZZK_SESSION_API_UNREACHABLE" -> "CHZZK 세션 API에 연결할 수 없습니다"
            "CHZZK_SESSION_API_FAILED" -> "CHZZK 세션 API 요청이 실패했습니다"
            "CHZZK_TOKEN_REFRESH_FAILED" -> "CHZZK 토큰 갱신에 실패했습니다"
            "CHZZK_ACCESS_TOKEN_MISSING" -> "CHZZK 토큰 갱신 응답에 accessToken이 없습니다"
            "CHZZK_PROVIDER_ERROR" -> "CHZZK provider 처리 중 알 수 없는 오류가 발생했습니다"
            "SOOP_INVALID_TOKEN" -> "SOOP 토큰이 만료되었거나 유효하지 않습니다"
            "SOOP_CHATINFO_FAILED" -> "SOOP 채팅 정보 API 요청이 실패했습니다"
            "SOOP_CHATINFO_DENIED" -> "SOOP 채팅 정보 API가 연결을 거부했습니다"
            "SOOP_CHATINFO_MISSING" -> "SOOP 채팅 정보 응답에 필수 값이 없습니다"
            "SOOP_INVALID_RESPONSE" -> "SOOP API 응답을 해석할 수 없습니다"
            "SOOP_TOKEN_REFRESH_FAILED" -> "SOOP 토큰 갱신에 실패했습니다"
            "SOOP_ACCESS_TOKEN_MISSING" -> "SOOP 토큰 갱신 응답에 accessToken이 없습니다"
            "SOOP_PROVIDER_ERROR" -> "SOOP provider 처리 중 알 수 없는 오류가 발생했습니다"
            else -> "알 수 없는 오류"
        }
    }
}

data class ActiveDonationSession(
    val playerUuid: String,
    val playerName: String,
    val platform: String,
)

private data class SessionRecord(
    val playerName: String,
    val session: DonationProviderSession,
)

private sealed interface TokenLoadResult {
    data class Loaded(val token: OAuthToken) : TokenLoadResult
    data class Failed(val cause: Throwable) : TokenLoadResult
    data object Missing : TokenLoadResult
}
