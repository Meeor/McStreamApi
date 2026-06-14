package kr.meeor.mcstreamapi.donation.chzzk

import kr.meeor.mcstreamapi.donation.DonationEventListener
import kr.meeor.mcstreamapi.donation.DonationProvider
import kr.meeor.mcstreamapi.donation.DonationProviderSession
import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore

class ChzzkDonationProvider(
    private val tokenRefresher: ChzzkTokenRefresher,
    private val tokenStore: TokenStore,
    private val sessionApi: ChzzkSessionApi = ChzzkSessionApi(),
    private val sessionTransport: ChzzkSessionTransport,
    private val reconnectPolicy: ProviderReconnectPolicy = ProviderReconnectPolicy(),
    private val logger: PluginLogger? = null,
) : DonationProvider {
    override val platform: String = PLATFORM

    override fun startSession(
        minecraftUuid: String,
        streamerId: String,
        streamerName: String,
        token: OAuthToken,
        listener: DonationEventListener,
    ): DonationProviderSession {
        val playerName = token.minecraftPlayerName ?: minecraftUuid
        logger?.debug("§e[진행] CHZZK 토큰 상태 확인 중: 플레이어=$playerName 채널=$streamerName")
        val usableToken = refreshIfNeeded(token)
        logger?.debug("§e[진행] CHZZK 세션 URL 요청 중: 플레이어=$playerName 채널=$streamerName")
        val sessionUrl = sessionApi.createUserSession(usableToken.accessToken).getOrElse { throwable ->
            val error = ChzzkDonationProviderException.from(throwable)
            logger?.warning("§c[실패] CHZZK 세션 URL 요청 실패: 플레이어=$playerName 채널=$streamerName 원인=${error.code}")
            throw error
        }
        logger?.debug(
            "§e[진행] CHZZK 세션 URL 확인: 플레이어=$playerName 채널=$streamerName 대상=${describeChzzkSessionUrl(sessionUrl)}",
        )

        logger?.debug("§e[진행] CHZZK WebSocket 연결 중: 플레이어=$playerName 채널=$streamerName")
        val socket = runCatching {
            sessionTransport.open(
                sessionUrl = sessionUrl,
                reconnectPolicy = reconnectPolicy,
                handler = object : ChzzkSessionHandler {
                    override fun onSocketOpened(attempt: Int) {
                        logger?.info(
                            "§a[성공] CHZZK WebSocket 접속 완료: 플레이어=$playerName 채널=$streamerName 시도=$attempt 세션키대기=true",
                        )
                    }

                    override fun onConnected(sessionKey: String) {
                        logger?.info("§a[성공] CHZZK 세션 연결 완료: 플레이어=$playerName 채널=$streamerName")
                        logger?.debug("§e[진행] CHZZK 후원 이벤트 구독 요청 중: 플레이어=$playerName 채널=$streamerName")
                        sessionApi.subscribeDonation(usableToken.accessToken, sessionKey).getOrElse { throwable ->
                            throw ChzzkDonationProviderException.from(throwable)
                        }
                        logger?.info("§a[성공] CHZZK 후원 이벤트 구독 완료: 플레이어=$playerName 채널=$streamerName")
                    }

                    override fun onDonation(dto: ChzzkDonationDto) {
                        listener.onDonation(dto.toDonationEvent(streamerName))
                    }

                    override fun onDisconnected(reason: String?) {
                        logger?.warning(
                            "§c[끊김] CHZZK 세션 연결 끊김: 플레이어=$playerName 채널=$streamerName 원인=${reason ?: "알 수 없음"}",
                        )
                    }

                    override fun onReconnectScheduled(attempt: Int, delayMillis: Long, reason: String?) {
                        logger?.debug(
                            "§e[대기] CHZZK 세션 재연결 예약: 플레이어=$playerName 채널=$streamerName " +
                                "시도=$attempt 대기=${delayMillis}ms 원인=${reason ?: "알 수 없음"}",
                        )
                    }

                    override fun onReconnecting(attempt: Int) {
                        logger?.debug(
                            "§e[진행] CHZZK WebSocket 연결 시도: 플레이어=$playerName 채널=$streamerName 시도=$attempt",
                        )
                    }

                    override fun onReconnectFailed(attempt: Int, reason: String?) {
                        logger?.warning(
                            "§c[실패] CHZZK WebSocket 연결 실패: 플레이어=$playerName 채널=$streamerName " +
                                "시도=$attempt 원인=${reason ?: "알 수 없음"}",
                        )
                    }
                },
            )
        }.getOrElse { throwable ->
            throw ChzzkDonationProviderException.from(throwable)
        }

        return DonationProviderSession {
            logger?.debug("§e[진행] CHZZK 세션 연결 종료 시도: 플레이어=$playerName 채널=$streamerName")
            socket.stop()
            logger?.info("§a[성공] CHZZK 세션 연결 종료 완료: 플레이어=$playerName 채널=$streamerName")
        }
    }

    private fun refreshIfNeeded(token: OAuthToken): OAuthToken {
        if (!tokenRefresher.shouldRefresh(token)) {
            return token
        }

        logger?.debug("§e[진행] CHZZK 토큰 갱신 중: 플레이어=${token.minecraftPlayerName ?: token.minecraftUuid}")
        val refreshed = tokenRefresher.refresh(token).getOrElse { throwable ->
            throw ChzzkDonationProviderException.from(throwable)
        }
        tokenStore.save(refreshed)
        logger?.info("§a[성공] CHZZK 토큰 갱신 완료: 플레이어=${token.minecraftPlayerName ?: token.minecraftUuid}")
        return refreshed
    }

    companion object {
        const val PLATFORM = "chzzk"
    }
}

class ChzzkDonationProviderException(
    val code: String,
    cause: Throwable? = null,
) : RuntimeException(code, cause) {
    companion object {
        fun from(throwable: Throwable): ChzzkDonationProviderException {
            return if (throwable is ChzzkDonationProviderException) {
                throwable
            } else {
                ChzzkDonationProviderException("CHZZK_PROVIDER_ERROR", throwable)
            }
        }
    }
}
