package kr.meeor.mcstreamapi.authserver.security

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

class RateLimiter(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun allow(key: String, maxRequests: Int, windowMillis: Long = 60_000): Boolean {
        if (maxRequests <= 0) {
            return false
        }

        val now = clock.millis()
        val bucket = buckets.compute(key) { _, current ->
            if (current == null || now - current.windowStartMillis >= windowMillis) {
                Bucket(now, 1)
            } else {
                current.copy(count = current.count + 1)
            }
        } ?: return false

        return bucket.count <= maxRequests
    }

    private data class Bucket(
        val windowStartMillis: Long,
        val count: Int,
    )
}
