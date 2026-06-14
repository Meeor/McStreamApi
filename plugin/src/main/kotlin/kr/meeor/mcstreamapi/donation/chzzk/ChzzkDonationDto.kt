package kr.meeor.mcstreamapi.donation.chzzk

import kr.meeor.mcstreamapi.donation.DonationEvent

data class ChzzkDonationDto(
    val donationType: String?,
    val channelId: String,
    val donatorChannelId: String?,
    val donatorNickname: String,
    val payAmount: Long,
    val donationText: String?,
    val messageTime: Long? = null,
) {
    fun toDonationEvent(streamerName: String): DonationEvent {
        return DonationEvent(
            platform = ChzzkDonationProvider.PLATFORM,
            streamerId = channelId,
            streamerName = streamerName,
            donatorName = donatorNickname,
            amount = payAmount,
            message = donationText,
            eventId = buildEventId(),
            occurredAtEpochSeconds = messageTime?.let { it / 1000 },
        )
    }

    private fun buildEventId(): String? {
        val time = messageTime ?: return null
        val donator = donatorChannelId ?: donatorNickname
        return "$channelId:$donator:$payAmount:$time"
    }
}
