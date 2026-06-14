package kr.meeor.mcstreamapi.placeholder

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class RandomConfigLoaderTest {
    @Test
    fun `loads simple and weighted random entries`() {
        val path = Files.createTempFile("random", ".yml")
        path.writeText(
            """
            animal:
              - "cow"
              - "pig"
            monster:
              - value: "zombie"
                chance: 50
                amount: "<2..4>"
                display: "좀비"
              - value: "skeleton"
                chance: 0
            """.trimIndent(),
        )

        val table = RandomConfigLoader().load(path)

        assertEquals(
            listOf(RandomEntry("cow", 100), RandomEntry("pig", 100)),
            table.entries["animal"],
        )
        assertEquals(
            listOf(
                RandomEntry("zombie", 50, RandomAmount.Range(2, 4), "좀비"),
                RandomEntry("skeleton", 0),
            ),
            table.entries["monster"],
        )
    }
}
