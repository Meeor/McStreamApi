package kr.meeor.mcstreamapi.authserver.security

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LogMaskerTest {
    @Test
    fun `masks token client secret and shared secret values`() {
        val masked = LogMasker().mask(
            "accessToken=access-123 refreshToken:refresh-456 clientSecret=client-789 sharedSecret=shared-000",
        )

        assertContains(masked, "accessToken=****")
        assertContains(masked, "refreshToken:****")
        assertContains(masked, "clientSecret=****")
        assertContains(masked, "sharedSecret=****")
        assertFalse(masked.contains("access-123"))
        assertFalse(masked.contains("refresh-456"))
        assertFalse(masked.contains("client-789"))
        assertFalse(masked.contains("shared-000"))
    }
}
