package kr.meeor.mcstreamapi.donation.soop

import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.token.OAuthToken

interface SoopDonationSessionTransport {
    fun open(
        token: OAuthToken,
        streamerId: String,
        streamerName: String,
        playerName: String,
        reconnectPolicy: ProviderReconnectPolicy,
        listener: (SoopDonationDto) -> Unit,
    ): SoopDonationSession
}

fun interface SoopDonationSession {
    fun stop()
}

object UnsupportedSoopDonationSessionTransport : SoopDonationSessionTransport {
    override fun open(
        token: OAuthToken,
        streamerId: String,
        streamerName: String,
        playerName: String,
        reconnectPolicy: ProviderReconnectPolicy,
        listener: (SoopDonationDto) -> Unit,
    ): SoopDonationSession {
        throw SoopDonationProviderException("SOOP_EVENT_API_NOT_CONFIGURED")
    }
}
