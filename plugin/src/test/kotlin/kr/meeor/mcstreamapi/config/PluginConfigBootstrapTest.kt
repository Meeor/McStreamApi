package kr.meeor.mcstreamapi.config

import kr.meeor.mcstreamapi.logging.PluginLogger
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginConfigBootstrapTest {
    @Test
    fun `creates runtime files and disables first run`() {
        val dataFolder = Files.createTempDirectory("mcstreamapi-plugin-config")
        val bootstrap = PluginConfigBootstrap(
            dataFolder = dataFolder,
            resourceProvider = { resourceName -> ByteArrayInputStream(defaultResource(resourceName).toByteArray()) },
            logger = PluginLogger(Logger.getLogger("test")),
        )

        val state = bootstrap.initialize()

        assertTrue(dataFolder.resolve("config.yml").exists())
        assertTrue(dataFolder.resolve("Api.yml").exists())
        assertTrue(dataFolder.resolve("random.yml").exists())
        assertTrue(dataFolder.resolve("custom-item.yml").exists())
        assertTrue(dataFolder.resolve("secret.key").exists())
        assertTrue(dataFolder.resolve("tokens").exists())
        assertTrue(state.firstRun)
        assertFalse(state.runtimeAvailable)
        assertFalse(dataFolder.resolve("secret.key").readText().contains("CHANGE_ME"))
    }

    @Test
    fun `creates custom item file from fallback when bundled custom item resource is missing`() {
        val dataFolder = Files.createTempDirectory("mcstreamapi-plugin-config")
        val bootstrap = PluginConfigBootstrap(
            dataFolder = dataFolder,
            resourceProvider = { resourceName ->
                if (resourceName == "defaults/custom-item.yml") {
                    null
                } else {
                    ByteArrayInputStream(defaultResource(resourceName).toByteArray())
                }
            },
            logger = PluginLogger(Logger.getLogger("test")),
        )

        bootstrap.initialize()

        assertTrue(dataFolder.resolve("custom-item.yml").exists())
        assertTrue(dataFolder.resolve("custom-item.yml").readText().contains("donation_diamond"))
    }

    private fun defaultResource(resourceName: String): String {
        return when (resourceName) {
            "defaults/config.yml" -> DEFAULT_CONFIG
            "defaults/Api.yml" -> "rewards: {}\n"
            "defaults/random.yml" -> "random: {}\n"
            "defaults/custom-item.yml" -> "items: {}\n"
            else -> error("unexpected resource $resourceName")
        }
    }

    private companion object {
        private const val DEFAULT_CONFIG = """
auth:
  serverBaseUrl: "https://auth.example.com/mca"
  sharedSecret: "CHANGE_ME_RANDOM_LONG_SECRET"
platforms:
  chzzk:
    enabled: true
    clientId: "CHZZK_CLIENT_ID"
    clientSecret: "CHZZK_CLIENT_SECRET"
  soop:
    enabled: true
    clientId: "SOOP_CLIENT_ID"
    clientSecret: "SOOP_CLIENT_SECRET"
"""
    }
}
