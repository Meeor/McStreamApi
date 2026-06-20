package kr.meeor.mcstreamapi.action

import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class GameTranslationToken(
    val encoded: String,
    val value: String,
) {
    companion object {
        private const val PREFIX = '\uE000'
        private const val SUFFIX = '\uE001'
        private val pattern = Regex("$PREFIX([A-Za-z0-9_-]+)$SUFFIX")

        fun encode(value: String): String {
            val payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
            return "$PREFIX$payload$SUFFIX"
        }

        fun findAll(message: String): List<GameTranslationToken> {
            return pattern.findAll(message).mapNotNull { match ->
                runCatching {
                    val decoded = String(
                        Base64.getUrlDecoder().decode(match.groupValues[1]),
                        StandardCharsets.UTF_8,
                    )
                    GameTranslationToken(match.value, decoded)
                }.getOrNull()
            }.toList()
        }
    }
}
