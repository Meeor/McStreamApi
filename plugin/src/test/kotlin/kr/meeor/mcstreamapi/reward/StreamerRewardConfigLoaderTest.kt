package kr.meeor.mcstreamapi.reward

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamerRewardConfigLoaderTest {
    @Test
    fun `loads rewards by normalized player uuid and platform`() {
        val path = createTempFile().also {
            it.writeText(
                """
                streamers:
                  "ABC-123":
                    SOOP:
                      - id: "custom"
                        amount: "1000"
                        actions:
                          - type: "broadcast"
                            message: "custom"
                    chzzk: []
                """.trimIndent(),
            )
        }

        val config = StreamerRewardConfigLoader().load(path)

        assertEquals("custom", config.rewards("abc-123", "soop").single()["id"])
        assertTrue(config.rewards("ABC-123", "chzzk").isEmpty())
        assertEquals(setOf("soop", "chzzk"), config.platforms("abc-123"))
    }
}
