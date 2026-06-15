package kr.meeor.mcstreamapi.logging

class LogMasker {
    fun mask(value: String): String {
        var masked = value
        SENSITIVE_KEYS.forEach { key ->
            masked = masked.replace(
                Regex("(?i)((?<![A-Za-z0-9_])${Regex.escape(key)}\\s*[:=]\\s*)([^\\s,&}]+)"),
                "$1****",
            )
        }
        return masked
    }

    companion object {
        private val SENSITIVE_KEYS = listOf(
            "accessToken",
            "refreshToken",
            "authorizationCode",
            "code",
            "clientSecret",
            "sharedSecret",
            "secret",
            "secret.key",
        )
    }
}
