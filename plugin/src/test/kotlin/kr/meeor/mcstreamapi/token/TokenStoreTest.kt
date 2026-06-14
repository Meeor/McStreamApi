package kr.meeor.mcstreamapi.token

import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStoreTest {
    @Test
    fun `saves encrypted token and loads it`() {
        val root = Files.createTempDirectory("mcstreamapi-token-store")
        val store = TokenStore(
            tokensDirectory = root.resolve("tokens"),
            secretKeyPath = root.resolve("secret.key"),
        )
        val token = token()

        store.save(token)
        val encryptedFiles = Files.list(root.resolve("tokens")).toList()
        val encryptedBytes = encryptedFiles.single().readBytes()
        val loaded = store.load("chzzk", "PLAYER-UUID").getOrThrow()

        assertFalse(encryptedBytes.decodeToString().contains("access-secret"))
        assertFalse(encryptedBytes.decodeToString().contains("refresh-secret"))
        assertEquals(token, loaded)
    }

    @Test
    fun `returns null for missing token`() {
        val root = Files.createTempDirectory("mcstreamapi-token-store")
        val store = TokenStore(
            tokensDirectory = root.resolve("tokens"),
            secretKeyPath = root.resolve("secret.key"),
        )

        assertNull(store.load("chzzk", "missing").getOrThrow())
    }

    @Test
    fun `does not use tampered token`() {
        val root = Files.createTempDirectory("mcstreamapi-token-store")
        val store = TokenStore(
            tokensDirectory = root.resolve("tokens"),
            secretKeyPath = root.resolve("secret.key"),
        )
        store.save(token())
        val tokenFile = Files.list(root.resolve("tokens")).toList().single()
        val tampered = tokenFile.readBytes()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        tokenFile.writeBytes(tampered)

        val result = store.load("chzzk", "PLAYER-UUID")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TokenCryptoException)
    }

    private fun token(): OAuthToken {
        return OAuthToken(
            platform = "chzzk",
            minecraftUuid = "PLAYER-UUID",
            accessToken = "access-secret",
            refreshToken = "refresh-secret",
            expiresAtEpochSeconds = 123456789,
            scope = "user:read",
        )
    }
}
