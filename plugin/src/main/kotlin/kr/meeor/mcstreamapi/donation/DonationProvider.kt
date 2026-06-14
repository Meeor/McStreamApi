package kr.meeor.mcstreamapi.donation

import kr.meeor.mcstreamapi.token.OAuthToken

interface DonationProvider {
    val platform: String

    fun startSession(
        minecraftUuid: String,
        streamerId: String,
        streamerName: String,
        token: OAuthToken,
        listener: DonationEventListener,
    ): DonationProviderSession
}

fun interface DonationEventListener {
    fun onDonation(event: DonationEvent)
}

fun interface DonationProviderSession {
    fun stop()
}
