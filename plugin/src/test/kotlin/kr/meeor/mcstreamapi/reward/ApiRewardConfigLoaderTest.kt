package kr.meeor.mcstreamapi.reward

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiRewardConfigLoaderTest {
    @Test
    fun `amount suggestions include only exact numeric amounts`() {
        val path = createTempFile().also {
            it.writeText(
                """
                rewards:
                  chzzk:
                    - amount: "1000"
                      actions:
                        - type: "broadcast"
                          message: "ok"
                    - amount: "5000+"
                      actions:
                        - type: "broadcast"
                          message: "ok"
                  soop:
                    - amount: "2000-3000"
                      actions:
                        - type: "broadcast"
                          message: "ok"
                """.trimIndent(),
            )
        }

        val suggestions = ApiRewardConfigLoader().load(path).amountSuggestions()

        assertEquals(listOf("1000"), suggestions)
    }
}
