package kr.meeor.mcstreamapi.donation

data class ProviderReconnectPolicy(
    val initialDelaySeconds: Long = 3,
    val maxDelaySeconds: Long = 60,
) {
    fun delayForAttempt(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        var delay = initialDelaySeconds
        repeat(safeAttempt - 1) {
            delay = (delay * 2).coerceAtMost(maxDelaySeconds)
        }
        return delay
    }
}
