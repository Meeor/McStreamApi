package kr.meeor.mcstreamapi.session

import kr.meeor.mcstreamapi.donation.DonationEvent
import java.time.Clock
import java.util.LinkedHashMap

class DonationEventDeduplicator(
    private val ttlMillis: Long = 10 * 60 * 1000,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val seen = LinkedHashMap<String, Long>()

    fun accept(event: DonationEvent): Boolean {
        evictExpired()
        val key = event.eventId?.takeIf { it.isNotBlank() } ?: bestEffortKey(event)
        if (seen.containsKey(key)) {
            return false
        }
        seen[key] = clock.millis()
        return true
    }

    private fun bestEffortKey(event: DonationEvent): String {
        return listOf(
            event.platform,
            event.streamerId,
            event.donatorName,
            event.amount.toString(),
            event.message.orEmpty(),
            event.occurredAtEpochSeconds?.toString().orEmpty(),
        ).joinToString("|")
    }

    private fun evictExpired() {
        val now = clock.millis()
        val iterator = seen.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > ttlMillis) {
                iterator.remove()
            }
        }
    }
}
