package kr.meeor.mcstreamapi.authserver.security

import java.security.MessageDigest

class SharedSecretValidator(
    private val expectedSecret: String,
) {
    fun isValid(actualSecret: String?): Boolean {
        if (actualSecret.isNullOrBlank()) {
            return false
        }

        val expected = expectedSecret.toByteArray(Charsets.UTF_8)
        val actual = actualSecret.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expected, actual)
    }
}
