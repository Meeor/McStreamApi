package kr.meeor.mcstreamapi.donation.chzzk

import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy

interface ChzzkSessionTransport {
    fun open(
        sessionUrl: String,
        reconnectPolicy: ProviderReconnectPolicy,
        handler: ChzzkSessionHandler,
    ): ChzzkSession
}

interface ChzzkSessionHandler {
    fun onSocketOpened(attempt: Int) = Unit

    fun onConnected(sessionKey: String)

    fun onDonation(dto: ChzzkDonationDto)

    fun onDisconnected(reason: String?) = Unit

    fun onReconnectScheduled(attempt: Int, delayMillis: Long, reason: String?) = Unit

    fun onReconnecting(attempt: Int) = Unit

    fun onReconnectFailed(attempt: Int, reason: String?) = Unit
}

fun interface ChzzkSession {
    fun stop()
}
