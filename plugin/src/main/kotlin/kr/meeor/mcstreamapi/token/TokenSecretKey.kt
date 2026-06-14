package kr.meeor.mcstreamapi.token

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64

class TokenSecretKey(
    private val path: Path,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun loadOrCreate(): ByteArray {
        if (!Files.exists(path)) {
            Files.createDirectories(path.parent)
            Files.writeString(path, generateKeyText())
        }

        return load()
    }

    fun load(): ByteArray {
        if (!Files.exists(path)) {
            throw TokenStoreException("secret.key is missing.")
        }

        val decoded = runCatching {
            Base64.getDecoder().decode(Files.readString(path).trim())
        }.getOrElse {
            throw TokenStoreException("secret.key is not valid Base64.")
        }

        if (decoded.size != TokenCrypto.KEY_BYTES) {
            throw TokenStoreException("secret.key must decode to 32 bytes.")
        }

        return decoded
    }

    private fun generateKeyText(): String {
        val key = ByteArray(TokenCrypto.KEY_BYTES)
        secureRandom.nextBytes(key)
        return Base64.getEncoder().encodeToString(key)
    }
}
