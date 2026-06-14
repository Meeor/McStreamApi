package kr.meeor.mcstreamapi.donation.soop

import kotlin.test.Test
import kotlin.test.assertEquals

class SoopDonationDtoTest {
    @Test
    fun `converts soop dto to donation event`() {
        val event = SoopDonationDto(
            eventId = "event-1",
            streamerId = "streamer-id",
            streamerName = "streamer",
            donatorName = "donator",
            amount = 1000,
            message = "hello",
            occurredAtEpochSeconds = 123,
        ).toDonationEvent()

        assertEquals("soop", event.platform)
        assertEquals("streamer-id", event.streamerId)
        assertEquals("donator", event.donatorName)
        assertEquals(1000, event.amount)
    }
}
