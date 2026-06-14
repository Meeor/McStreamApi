package kr.meeor.mcstreamapi.donation.soop

import kr.meeor.mcstreamapi.donation.DonationEventListener
import kr.meeor.mcstreamapi.donation.DonationProvider
import kr.meeor.mcstreamapi.donation.DonationProviderSession
import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore

class SoopDonationProvider(
    private val tokenRefresher: SoopTokenRefresher,
    private val tokenStore: TokenStore,
    private val sessionTransport: SoopDonationSessionTransport = UnsupportedSoopDonationSessionTransport,
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
        logger?.debug("§e[진행] SOOP 토큰 상태 확인 중: 플레이어=$playerName 채널=$streamerName")
        val usableToken = refreshIfNeeded(token)
        logger?.debug("§e[진행] SOOP 채팅 세션 연결 중: 플레이어=$playerName 채널=$streamerName")
        val session = sessionTransport.open(
            token = usableToken,
            streamerId = streamerId,
            streamerName = streamerName,
            playerName = playerName,
            reconnectPolicy = reconnectPolicy,
            listener = { dto -> listener.onDonation(dto.toDonationEvent()) },
        )
        logger?.debug("§e[진행] SOOP 채팅 세션 시작 등록 완료: 플레이어=$playerName 채널=$streamerName")
        return DonationProviderSession {
            logger?.debug("§e[진행] SOOP 세션 연결 종료 시도: 플레이어=$playerName 채널=$streamerName")
            session.stop()
            logger?.info("§a[성공] SOOP 세션 연결 종료 완료: 플레이어=$playerName 채널=$streamerName")
        }
    }

    private fun refreshIfNeeded(token: OAuthToken): OAuthToken {
        if (!tokenRefresher.shouldRefresh(token)) {
            return token
        }

        logger?.debug("§e[진행] SOOP 토큰 갱신 중: 플레이어=${token.minecraftPlayerName ?: token.minecraftUuid}")
        val refreshed = tokenRefresher.refresh(token).getOrElse { throwable ->
            throw SoopDonationProviderException.from(throwable)
        }
        tokenStore.save(refreshed)
        logger?.info("§a[성공] SOOP 토큰 갱신 완료: 플레이어=${token.minecraftPlayerName ?: token.minecraftUuid}")
        return refreshed
    }

    companion object {
        const val PLATFORM = "soop"
    }
}

class SoopDonationProviderException(
    val code: String,
) : RuntimeException(code) {
    companion object {
        fun from(throwable: Throwable): SoopDonationProviderException {
            return if (throwable is SoopDonationProviderException) {
                throwable
            } else {
                SoopDonationProviderException("SOOP_PROVIDER_ERROR")
            }
        }
    }
}
