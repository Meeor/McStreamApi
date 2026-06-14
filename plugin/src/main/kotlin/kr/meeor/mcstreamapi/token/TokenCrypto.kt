package kr.meeor.mcstreamapi.token

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TokenCrypto(
    private val key: ByteArray,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    init {
        require(key.size == KEY_BYTES) { "secret.key must decode to 32 bytes." }
    }

    fun encrypt(plainText: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_BITS, nonce))
        val cipherText = cipher.doFinal(plainText)

        return nonce + cipherText
    }

    fun decrypt(encrypted: ByteArray): Result<ByteArray> {
        if (encrypted.size <= NONCE_BYTES) {
            return Result.failure(TokenCryptoException("Encrypted token payload is too short."))
        }

        return runCatching {
            val nonce = encrypted.copyOfRange(0, NONCE_BYTES)
            val cipherText = encrypted.copyOfRange(NONCE_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(cipherText)
        }.recoverCatching { throwable ->
            if (throwable is AEADBadTagException) {
                throw TokenCryptoException("Token authentication tag validation failed.")
            }
            throw TokenCryptoException("Token decrypt failed.")
        }
    }

    companion object {
        const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class TokenCryptoException(message: String) : RuntimeException(message)
