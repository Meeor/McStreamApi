package kr.meeor.mcstreamapi.token

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

class TokenStore(
    private val tokensDirectory: Path,
    secretKeyPath: Path,
) {
    private val secretKey = TokenSecretKey(secretKeyPath)

    fun save(token: OAuthToken) {
        Files.createDirectories(tokensDirectory)
        val crypto = TokenCrypto(secretKey.loadOrCreate())
        val encrypted = crypto.encrypt(token.toJson().toByteArray(StandardCharsets.UTF_8))
        Files.write(tokenPath(token.platform, token.minecraftUuid), encrypted)
    }

    fun load(platform: String, minecraftUuid: String): Result<OAuthToken?> {
        val path = tokenPath(platform, minecraftUuid)
        if (!Files.exists(path)) {
            return Result.success(null)
        }

        return runCatching {
            val crypto = TokenCrypto(secretKey.load())
            val plainText = crypto.decrypt(Files.readAllBytes(path)).getOrThrow()
            OAuthToken.fromJson(plainText.toString(StandardCharsets.UTF_8)).getOrThrow()
        }.recoverCatching { throwable ->
            when (throwable) {
                is TokenCryptoException -> throw throwable
                is TokenStoreException -> throw throwable
                else -> throw TokenStoreException("Token load failed.")
            }
        }
    }

    fun connectedPlayerNames(knownNamesByUuid: Map<String, String> = emptyMap()): List<String> {
        return connectedTokens(knownNamesByUuid)
            .mapNotNull { it.minecraftPlayerName }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun connectedTokens(knownNamesByUuid: Map<String, String> = emptyMap()): List<ConnectedTokenInfo> {
        if (!Files.exists(tokensDirectory)) {
            return emptyList()
        }

        return Files.list(tokensDirectory).use { paths ->
            paths
                .asSequence()
                .filter { it.fileName.toString().endsWith(".json.enc") }
                .mapNotNull { loadTokenFile(it).getOrNull() }
                .map { token ->
                    ConnectedTokenInfo(
                        platform = token.platform,
                        minecraftUuid = token.minecraftUuid,
                        minecraftPlayerName = token.minecraftPlayerName ?: knownNamesByUuid[token.minecraftUuid],
                        channelId = token.channelId,
                        channelName = token.channelName,
                        expiresAtEpochSeconds = token.expiresAtEpochSeconds,
                    )
                }
                .sortedWith(compareBy<ConnectedTokenInfo> { it.minecraftPlayerName ?: it.minecraftUuid }.thenBy { it.platform })
                .toList()
        }
    }

    private fun tokenPath(platform: String, minecraftUuid: String): Path {
        val safePlatform = safeSegment(platform)
        val safeUuid = safeSegment(minecraftUuid)
        return tokensDirectory.resolve("$safePlatform-$safeUuid.json.enc")
    }

    private fun safeSegment(value: String): String {
        val normalized = value.lowercase().replace(Regex("[^a-z0-9_.-]"), "_")
        if (normalized.isBlank()) {
            throw TokenStoreException("Token path segment is empty.")
        }
        return normalized
    }

    private fun loadTokenFile(path: Path): Result<OAuthToken> {
        return runCatching {
            val crypto = TokenCrypto(secretKey.load())
            val plainText = crypto.decrypt(Files.readAllBytes(path)).getOrThrow()
            OAuthToken.fromJson(plainText.toString(StandardCharsets.UTF_8)).getOrThrow()
        }
    }
}

data class ConnectedTokenInfo(
    val platform: String,
    val minecraftUuid: String,
    val minecraftPlayerName: String?,
    val channelId: String?,
    val channelName: String?,
    val expiresAtEpochSeconds: Long,
)

class TokenStoreException(message: String) : RuntimeException(message)
