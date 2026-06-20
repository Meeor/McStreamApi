package kr.meeor.mcstreamapi.action

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorCodesTest {
    @Test
    fun `translates legacy and hex color codes`() {
        assertEquals(
            "§a초록 §x§1§2§A§b§3§FHEX §l굵게§r",
            translateColorCodes("&a초록 &#12Ab3FHEX &l굵게&r"),
        )
    }

    @Test
    fun `leaves invalid hex color codes unchanged`() {
        assertEquals("&#12GG34invalid", translateColorCodes("&#12GG34invalid"))
    }

    @Test
    fun `keeps bold active across legacy and hex colors until reset`() {
        assertEquals(
            "§l굵게 §a§l초록 §x§0§0§F§F§F§F§lHEX §r§b일반",
            translateColorCodes("&l굵게 &a초록 &#00FFFFHEX &r&b일반"),
        )
    }
}
