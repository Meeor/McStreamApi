package kr.meeor.mcstreamapi.config

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginConfigValidatorTest {
    private val validator = PluginConfigValidator()

    @Test
    fun `rejects placeholder auth and platform credentials`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText(
            """
            auth:
              serverBaseUrl: "https://auth.example.com/mca"
              sharedSecret: "CHANGE_ME_RANDOM_LONG_SECRET"
            platforms:
              chzzk:
                enabled: true
                clientId: "CHZZK_CLIENT_ID"
                clientSecret: "CHZZK_CLIENT_SECRET"
              soop:
                enabled: false
            """.trimIndent(),
        )

        val result = validator.validate(config)

        assertFalse(result.authAvailable)
        assertTrue("chzzk" in result.disabledPlatforms)
        assertTrue("soop" in result.disabledPlatforms)
        assertTrue(result.enabledPlatforms.isEmpty())
    }

    @Test
    fun `enables only platforms with configured credentials`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText(
            """
            auth:
              serverBaseUrl: "http://localhost:8080/mca"
              sharedSecret: "12345678901234567890123456789012"
            platforms:
              chzzk:
                enabled: true
                clientId: "configured-client-id"
                clientSecret: "configured-client-secret"
              soop:
                enabled: true
                clientId: "SOOP_CLIENT_ID"
                clientSecret: "SOOP_CLIENT_SECRET"
            """.trimIndent(),
        )

        val result = validator.validate(config)

        assertTrue(result.authAvailable)
        assertEquals(setOf("chzzk"), result.enabledPlatforms)
        assertTrue("soop" in result.disabledPlatforms)
    }

    @Test
    fun `missing config disables runtime with warning`() {
        val config = Files.createTempDirectory("mcstreamapi-config").resolve("missing.yml")

        val result = validator.validate(config)

        assertFalse(result.authAvailable)
        assertEquals(setOf("chzzk", "soop"), result.disabledPlatforms)
        assertTrue(result.warnings.any { it.startsWith("CONFIG_MISSING") })
    }

    @Test
    fun `invalid yaml disables runtime with warning`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText("auth:\n  serverBaseUrl: [broken")

        val result = validator.validate(config)

        assertFalse(result.authAvailable)
        assertEquals(setOf("chzzk", "soop"), result.disabledPlatforms)
        assertTrue(result.warnings.any { it.startsWith("CONFIG_INVALID_YAML") })
    }

    @Test
    fun `invalid auth server url disables auth only`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText(
            """
            auth:
              serverBaseUrl: "not a url"
              sharedSecret: "12345678901234567890123456789012"
            platforms:
              chzzk:
                enabled: true
                clientId: "configured-client-id"
                clientSecret: "configured-client-secret"
            """.trimIndent(),
        )

        val result = validator.validate(config)

        assertFalse(result.authAvailable)
        assertEquals(setOf("chzzk"), result.enabledPlatforms)
        assertTrue(result.warnings.any { it == "AUTH_DISABLED reason=INVALID_SERVER_BASE_URL" })
    }

    @Test
    fun `soop optional balloon event flags default false and can be enabled`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText(
            """
            auth:
              serverBaseUrl: "http://localhost:8080/mca"
              sharedSecret: "12345678901234567890123456789012"
            platforms:
              soop:
                enabled: true
                clientId: "configured-client-id"
                clientSecret: "configured-client-secret"
                receiveAdBalloons: true
                receiveVideoBalloons: true
            """.trimIndent(),
        )

        val result = validator.validate(config)
        val soop = result.platformConfigs.getValue("soop")

        assertTrue(soop.receiveAdBalloons)
        assertTrue(soop.receiveVideoBalloons)
    }

    @Test
    fun `soop optional balloon event flags are false when omitted`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText(
            """
            auth:
              serverBaseUrl: "http://localhost:8080/mca"
              sharedSecret: "12345678901234567890123456789012"
            platforms:
              soop:
                enabled: true
                clientId: "configured-client-id"
                clientSecret: "configured-client-secret"
            """.trimIndent(),
        )

        val result = validator.validate(config)
        val soop = result.platformConfigs.getValue("soop")

        assertFalse(soop.receiveAdBalloons)
        assertFalse(soop.receiveVideoBalloons)
    }

    @Test
    fun `logging debug flag is parsed from config`() {
        val config = Files.createTempFile("mcstreamapi-config", ".yml")
        config.writeText(
            """
            auth:
              serverBaseUrl: "http://localhost:8080/mca"
              sharedSecret: "12345678901234567890123456789012"
            logging:
              debug: true
            platforms:
              chzzk:
                enabled: false
            """.trimIndent(),
        )

        val result = validator.validate(config)

        assertTrue(result.loggingConfig.debug)
    }
}
