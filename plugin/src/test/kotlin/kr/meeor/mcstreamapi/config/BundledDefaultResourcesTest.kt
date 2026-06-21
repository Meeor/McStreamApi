package kr.meeor.mcstreamapi.config

import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertTrue

class BundledDefaultResourcesTest {
    @Test
    fun `bundled yaml defaults are valid and explain required and optional fields`() {
        val resources = listOf(
            "defaults/config.yml",
            "defaults/Api.yml",
            "defaults/random.yml",
            "defaults/custom-item.yml",
            "defaults/streamer-rewards.yml",
        )

        resources.forEach { resourceName ->
            val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resourceName)) {
                "Missing bundled resource: $resourceName"
            }
            val content: String = stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            assertTrue(content.contains("[필수]"), "Required-field guidance missing: $resourceName")
            assertTrue(content.contains("[선택"), "Optional-field guidance missing: $resourceName")
            val yaml = requireNotNull(Yaml().load<Map<String, Any?>>(content)) { "Invalid YAML: $resourceName" }
            when (resourceName) {
                "defaults/Api.yml" -> assertTrue((yaml["rewards"] as? Map<*, *>)?.isEmpty() == true)
                "defaults/random.yml" -> assertTrue(yaml.isEmpty())
                "defaults/custom-item.yml" -> assertTrue((yaml["items"] as? Map<*, *>)?.isEmpty() == true)
                "defaults/streamer-rewards.yml" -> assertTrue((yaml["streamers"] as? Map<*, *>)?.isEmpty() == true)
            }
        }
    }
}
