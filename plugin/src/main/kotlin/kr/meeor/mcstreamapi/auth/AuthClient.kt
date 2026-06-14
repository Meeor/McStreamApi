package kr.meeor.mcstreamapi.auth

import kr.meeor.mcstreamapi.config.PluginAuthConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

interface AuthClient {
    fun registerPairing(request: PairingRegisterCommand): Result<PairingRegisterResult>

    fun getPairing(pairingCode: String): Result<PairingStatusResult>
}

data class PairingRegisterCommand(
    val pairingCode: String,
    val platform: String,
    val minecraftPlayerName: String,
    val minecraftUuid: String,
)

data class PairingRegisterResult(
    val pairingCode: String,
    val status: String,
    val expiresInSeconds: Long,
    val authorizeUrl: String,
)

data class PairingStatusResult(
    val pairingCode: String,
    val status: String,
    val platform: String?,
    val minecraftUuid: String?,
    val channelId: String?,
    val channelName: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenType: String?,
    val scopes: List<String>,
    val expiresAt: Instant?,
)

class JavaAuthClient(
    private val config: PluginAuthConfig,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : AuthClient {
    override fun registerPairing(request: PairingRegisterCommand): Result<PairingRegisterResult> {
        return sendJson(
            path = "/api/pairing",
            method = "POST",
            body = buildJsonObject {
                put("pairingCode", JsonPrimitive(request.pairingCode))
                put("platform", JsonPrimitive(request.platform))
                put("minecraftPlayerName", JsonPrimitive(request.minecraftPlayerName))
                put("minecraftUuid", JsonPrimitive(request.minecraftUuid))
            }.toString(),
        ).map { response ->
            val root = Json.parseToJsonElement(response).jsonObject
            PairingRegisterResult(
                pairingCode = root.requiredString("pairingCode"),
                status = root.requiredString("status"),
                expiresInSeconds = root.requiredLong("expiresInSeconds"),
                authorizeUrl = root.requiredString("authorizeUrl"),
            )
        }
    }

    override fun getPairing(pairingCode: String): Result<PairingStatusResult> {
        return sendJson(
            path = "/api/pairing/$pairingCode",
            method = "GET",
            body = null,
        ).map { response ->
            val root = Json.parseToJsonElement(response).jsonObject
            PairingStatusResult(
                pairingCode = root.requiredString("pairingCode"),
                status = root.requiredString("status"),
                platform = root.optionalString("platform"),
                minecraftUuid = root.optionalString("minecraftUuid"),
                channelId = root.optionalString("channelId"),
                channelName = root.optionalString("channelName"),
                accessToken = root.optionalString("accessToken"),
                refreshToken = root.optionalString("refreshToken"),
                tokenType = root.optionalString("tokenType"),
                scopes = root.optionalStringList("scopes"),
                expiresAt = root.optionalString("expiresAt")?.let { Instant.parse(it) },
            )
        }
    }

    private fun sendJson(path: String, method: String, body: String?): Result<String> {
        return runCatching {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create("${config.serverBaseUrl}$path"))
                .header(SHARED_SECRET_HEADER, config.sharedSecret)
                .header("Content-Type", "application/json")

            val request = if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build()
            }

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw AuthClientException(mapStatus(response.statusCode()))
            }
            response.body()
        }.recoverCatching { throwable ->
            if (throwable is AuthClientException) {
                throw throwable
            }
            throw AuthClientException("AUTH_SERVER_UNREACHABLE")
        }
    }

    private fun mapStatus(statusCode: Int): String {
        return when (statusCode) {
            401 -> "INVALID_SHARED_SECRET"
            404 -> "PAIRING_NOT_FOUND"
            409 -> "PAIRING_FAILED"
            410 -> "PAIRING_EXPIRED"
            else -> "AUTH_SERVER_ERROR"
        }
    }

    companion object {
        private const val SHARED_SECRET_HEADER = "X-McStreamApi-Secret"
    }
}

class AuthClientException(val code: String) : RuntimeException(code)

private fun JsonObject.requiredString(key: String): String {
    return optionalString(key)?.takeIf { it.isNotBlank() }
        ?: throw AuthClientException("INVALID_AUTH_RESPONSE")
}

private fun JsonObject.requiredLong(key: String): Long {
    return this[key]?.jsonPrimitive?.longOrNull ?: throw AuthClientException("INVALID_AUTH_RESPONSE")
}

private fun JsonObject.optionalString(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.optionalStringList(key: String): List<String> {
    val value = this[key] ?: return emptyList()
    return when (value) {
        is JsonArray -> value.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        }
        else -> emptyList()
    }
}
