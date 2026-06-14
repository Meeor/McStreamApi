package kr.meeor.mcstreamapi.authserver.pairing

import java.time.Instant
import java.util.UUID

enum class PairingStatus {
    PENDING,
    AUTHORIZED,
    CONSUMED,
    EXPIRED,
    FAILED,
}

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String,
    val scopes: Set<String>,
    val expiresAt: Instant,
)

data class ChannelInfo(
    val platform: String,
    val channelId: String,
    val channelName: String,
)

data class PairingSession(
    val pairingCode: String,
    val platform: String,
    val playerUuid: UUID,
    val playerName: String,
    val status: PairingStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val authorizedAt: Instant? = null,
    val consumedAt: Instant? = null,
    val expiredAt: Instant? = null,
    val failedAt: Instant? = null,
    val failureReason: String? = null,
    val channelInfo: ChannelInfo? = null,
    val token: OAuthToken? = null,
) {
    init {
        require(token == null || status == PairingStatus.AUTHORIZED) {
            "OAuthToken can exist only while the session is AUTHORIZED."
        }
    }

    val isTerminal: Boolean
        get() = status == PairingStatus.CONSUMED ||
            status == PairingStatus.EXPIRED ||
            status == PairingStatus.FAILED
}
