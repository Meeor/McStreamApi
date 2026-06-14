package kr.meeor.mcstreamapi.donation

import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderReconnectPolicyTest {
    @Test
    fun `uses capped exponential delay`() {
        val policy = ProviderReconnectPolicy(initialDelaySeconds = 3, maxDelaySeconds = 10)

        assertEquals(3, policy.delayForAttempt(1))
        assertEquals(6, policy.delayForAttempt(2))
        assertEquals(10, policy.delayForAttempt(3))
        assertEquals(10, policy.delayForAttempt(4))
    }
}
