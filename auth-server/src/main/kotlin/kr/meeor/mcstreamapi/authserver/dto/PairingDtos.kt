package kr.meeor.mcstreamapi.authserver.dto

import kotlinx.serialization.Serializable

@Serializable
data class PairingRegisterRequest(
    val pairingCode: String,
    val platform: String,
    val minecraftPlayerName: String,
    val minecraftUuid: String,
)

@Serializable
data class PairingRegisterResponse(
    val success: Boolean = true,
    val pairingCode: String,
    val status: String,
    val expiresInSeconds: Long,
    val authorizeUrl: String,
)

@Serializable
data class PairingStatusResponse(
    val success: Boolean = true,
    val pairingCode: String,
    val status: String,
    val platform: String? = null,
    val minecraftPlayerName: String? = null,
    val minecraftUuid: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val scopes: List<String>? = null,
    val expiresAt: String? = null,
)

@Serializable
data class PairingDeleteResponse(
    val success: Boolean = true,
    val pairingCode: String,
    val status: String,
)
