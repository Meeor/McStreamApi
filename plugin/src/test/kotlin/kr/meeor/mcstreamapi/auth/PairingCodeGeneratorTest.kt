package kr.meeor.mcstreamapi.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingCodeGeneratorTest {
    @Test
    fun `generates valid pairing code`() {
        val code = PairingCodeGenerator().generate()

        assertEquals(8, code.length)
        assertTrue(Regex("^[A-Z0-9]{8}$").matches(code))
    }
}
