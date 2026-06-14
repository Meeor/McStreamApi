package kr.meeor.mcstreamapi.token

import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TokenSecretKeyTest {
    @Test
    fun `creates and loads base64 key`() {
        val keyPath = Files.createTempDirectory("mcstreamapi-token-key").resolve("secret.key")

        val key = TokenSecretKey(keyPath).loadOrCreate()

        assertTrue(keyPath.exists())
        assertEquals(32, key.size)
        assertEquals(32, Base64.getDecoder().decode(Files.readString(keyPath).trim()).size)
    }

    @Test
    fun `rejects invalid key`() {
        val keyPath = Files.createTempDirectory("mcstreamapi-token-key").resolve("secret.key")
        keyPath.writeText("bad")

        assertFailsWith<TokenStoreException> {
            TokenSecretKey(keyPath).load()
        }
    }
}
