package kr.meeor.mcstreamapi.authserver.http

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: Boolean = true,
)

@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: String,
    val message: String,
    val requestId: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
)

@Serializable
data class ReadyResponse(
    val status: String,
    val enabledPlatforms: List<String>,
)
