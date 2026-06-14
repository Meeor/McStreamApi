package kr.meeor.mcstreamapi.token

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenCryptoTest {
    @Test
    fun `encrypts and decrypts payload`() {
        val crypto = TokenCrypto(ByteArray(32) { 1 })
        val plainText = "accessToken=secret".toByteArray()

        val encrypted = crypto.encrypt(plainText)
        val decrypted = crypto.decrypt(encrypted).getOrThrow()

        assertTrue(!encrypted.decodeToString().contains("secret"))
        assertEquals("accessToken=secret", decrypted.decodeToString())
    }

    @Test
    fun `fails when authentication tag is modified`() {
        val crypto = TokenCrypto(ByteArray(32) { 1 })
        val encrypted = crypto.encrypt("secret".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        val result = crypto.decrypt(encrypted)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TokenCryptoException)
    }
}
