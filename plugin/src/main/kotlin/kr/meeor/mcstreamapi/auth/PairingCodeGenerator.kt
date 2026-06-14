package kr.meeor.mcstreamapi.auth

import java.security.SecureRandom

open class PairingCodeGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    open fun generate(length: Int = DEFAULT_LENGTH): String {
        require(length in 6..16) { "pairing code length must be 6..16" }
        return buildString(length) {
            repeat(length) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
    }

    companion object {
        private const val DEFAULT_LENGTH = 8
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
