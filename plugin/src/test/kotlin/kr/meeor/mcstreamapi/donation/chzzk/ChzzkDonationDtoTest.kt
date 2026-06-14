package kr.meeor.mcstreamapi.donation.chzzk

import kotlin.test.Test
import kotlin.test.assertEquals

class ChzzkDonationDtoTest {
    @Test
    fun `converts chzzk donation dto to donation event`() {
        val event = ChzzkDonationDto(
            donationType = "CHAT",
            channelId = "channel",
            donatorChannelId = "donator-channel",
            donatorNickname = "donator",
            payAmount = 1000,
            donationText = "hello",
            messageTime = 123000,
        ).toDonationEvent(streamerName = "streamer")

        assertEquals("chzzk", event.platform)
        assertEquals("channel", event.streamerId)
        assertEquals("streamer", event.streamerName)
        assertEquals("donator", event.donatorName)
        assertEquals(1000, event.amount)
        assertEquals("hello", event.message)
        assertEquals("channel:donator-channel:1000:123000", event.eventId)
        assertEquals(123, event.occurredAtEpochSeconds)
    }
}
