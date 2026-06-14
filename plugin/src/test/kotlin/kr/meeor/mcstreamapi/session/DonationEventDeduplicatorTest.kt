package kr.meeor.mcstreamapi.session

import kr.meeor.mcstreamapi.donation.DonationEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DonationEventDeduplicatorTest {
    @Test
    fun `rejects duplicate event id`() {
        val deduplicator = DonationEventDeduplicator()
        val event = event(eventId = "event-1")

        assertTrue(deduplicator.accept(event))
        assertFalse(deduplicator.accept(event))
    }

    @Test
    fun `uses best effort key when event id is missing`() {
        val deduplicator = DonationEventDeduplicator()
        val event = event(eventId = null)

        assertTrue(deduplicator.accept(event))
        assertFalse(deduplicator.accept(event))
    }

    @Test
    fun `expires cache entries`() {
        val clock = MutableClock(0)
        val deduplicator = DonationEventDeduplicator(ttlMillis = 100, clock = clock)
        val event = event(eventId = "event-1")

        assertTrue(deduplicator.accept(event))
        clock.millis = 101
        assertTrue(deduplicator.accept(event))
    }

    private fun event(eventId: String?): DonationEvent {
        return DonationEvent(
            platform = "chzzk",
            streamerId = "streamer",
            streamerName = "streamer",
            donatorName = "donator",
            amount = 1000,
            message = "hello",
            eventId = eventId,
            occurredAtEpochSeconds = 1,
        )
    }

    private class MutableClock(var millis: Long) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(millis)

        override fun millis(): Long = millis
    }
}
