package kr.meeor.mcstreamapi.donation

data class DonationEvent(
    val platform: String,
    val streamerId: String,
    val streamerName: String,
    val donatorName: String,
    val amount: Long,
    val message: String?,
    val eventId: String?,
    val occurredAtEpochSeconds: Long?,
)
