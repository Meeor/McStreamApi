package kr.meeor.mcstreamapi.donation.soop

import kr.meeor.mcstreamapi.donation.DonationEvent

data class SoopDonationDto(
    val eventId: String?,
    val streamerId: String,
    val streamerName: String,
    val donatorName: String,
    val amount: Long,
    val message: String?,
    val occurredAtEpochSeconds: Long?,
) {
    fun toDonationEvent(): DonationEvent {
        return DonationEvent(
            platform = SoopDonationProvider.PLATFORM,
            streamerId = streamerId,
            streamerName = streamerName,
            donatorName = donatorName,
            amount = amount,
            message = message,
            eventId = eventId,
            occurredAtEpochSeconds = occurredAtEpochSeconds,
        )
    }
}
