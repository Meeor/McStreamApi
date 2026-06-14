package kr.meeor.mcstreamapi.authserver.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.dto.PairingDeleteResponse
import kr.meeor.mcstreamapi.authserver.dto.PairingRegisterRequest
import kr.meeor.mcstreamapi.authserver.dto.PairingRegisterResponse
import kr.meeor.mcstreamapi.authserver.dto.PairingStatusResponse
import kr.meeor.mcstreamapi.authserver.http.respondError
import kr.meeor.mcstreamapi.authserver.pairing.OAuthToken
import kr.meeor.mcstreamapi.authserver.pairing.PairingException
import kr.meeor.mcstreamapi.authserver.pairing.PairingService
import kr.meeor.mcstreamapi.authserver.pairing.PairingSession
import kr.meeor.mcstreamapi.authserver.pairing.PairingStatus
import kr.meeor.mcstreamapi.authserver.security.SharedSecretValidator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val SHARED_SECRET_HEADER = "X-McStreamApi-Secret"
private val pairingCodeRegex = Regex("^[A-Z0-9]{6,16}$")
private val minecraftPlayerNameRegex = Regex("^[A-Za-z0-9_]{3,16}$")

fun Route.pairingRoutes(
    validatedConfig: ValidatedConfig,
    pairingService: PairingService,
    sharedSecretValidator: SharedSecretValidator,
) {
    post("/api/pairing") {
        if (!call.requireSharedSecret(sharedSecretValidator)) {
            return@post
        }

        val request = runCatching { call.receive<PairingRegisterRequest>() }
            .getOrElse {
                call.respondError(HttpStatusCode.BadRequest, "INVALID_REQUEST_BODY", "Invalid request body.")
                return@post
            }

        val normalizedPlatform = request.platform.trim().lowercase()
        val validationError = validateRegisterRequest(request, normalizedPlatform, validatedConfig)
        if (validationError != null) {
            call.respondError(HttpStatusCode.BadRequest, validationError, "Invalid pairing request.")
            return@post
        }

        val playerUuid = parseMinecraftUuid(request.minecraftUuid)
            ?: run {
                call.respondError(HttpStatusCode.BadRequest, "INVALID_MINECRAFT_UUID", "Invalid minecraftUuid.")
                return@post
            }

        val session = runCatching {
            pairingService.createPending(
                platform = normalizedPlatform,
                playerUuid = playerUuid,
                playerName = request.minecraftPlayerName.trim(),
                pairingCode = request.pairingCode.trim(),
            )
        }.getOrElse { cause ->
            when (cause) {
                is IllegalArgumentException -> {
                    call.respondError(HttpStatusCode.Conflict, "PAIRING_ALREADY_EXISTS", "Pairing already exists.")
                    return@post
                }

                else -> throw cause
            }
        }

        call.respond(
            HttpStatusCode.Created,
            PairingRegisterResponse(
                pairingCode = session.pairingCode,
                status = session.status.name,
                expiresInSeconds = validatedConfig.config.security.pairingExpireSeconds,
                authorizeUrl = buildAuthorizeUrl(validatedConfig.config.server.publicBaseUrl, session),
            ),
        )
    }

    get("/api/pairing/{pairingCode}") {
        if (!call.requireSharedSecret(sharedSecretValidator)) {
            return@get
        }

        val pairingCode = call.parameters["pairingCode"].orEmpty()
        if (!isValidPairingCode(pairingCode)) {
            call.respondError(HttpStatusCode.BadRequest, "INVALID_PAIRING_CODE", "Invalid pairingCode.")
            return@get
        }

        val session = runCatching { pairingService.poll(pairingCode) }
            .getOrElse { cause ->
                if (cause is PairingException.NotFound) {
                    call.respondError(HttpStatusCode.NotFound, "PAIRING_NOT_FOUND", "Pairing not found.")
                    return@get
                }
                throw cause
            }

        val statusCode = when (session.status) {
            PairingStatus.EXPIRED -> HttpStatusCode.Gone
            PairingStatus.CONSUMED -> HttpStatusCode.Conflict
            PairingStatus.FAILED -> HttpStatusCode.Conflict
            else -> HttpStatusCode.OK
        }

        if (session.status == PairingStatus.CONSUMED) {
            call.respondError(statusCode, "PAIRING_CONSUMED", "Pairing token was already consumed.")
            return@get
        }
        if (session.status == PairingStatus.EXPIRED) {
            call.respondError(statusCode, "PAIRING_EXPIRED", "Pairing expired.")
            return@get
        }
        if (session.status == PairingStatus.FAILED) {
            call.respondError(statusCode, "PAIRING_FAILED", "Pairing failed.")
            return@get
        }

        call.respond(statusCode, session.toStatusResponse())
    }

    delete("/api/pairing/{pairingCode}") {
        if (!call.requireSharedSecret(sharedSecretValidator)) {
            return@delete
        }

        val pairingCode = call.parameters["pairingCode"].orEmpty()
        if (!isValidPairingCode(pairingCode)) {
            call.respondError(HttpStatusCode.BadRequest, "INVALID_PAIRING_CODE", "Invalid pairingCode.")
            return@delete
        }

        val deleted = runCatching { pairingService.delete(pairingCode) }
            .getOrElse { cause ->
                if (cause is PairingException.NotFound) {
                    call.respondError(HttpStatusCode.NotFound, "PAIRING_NOT_FOUND", "Pairing not found.")
                    return@delete
                }
                throw cause
            }

        call.respond(
            PairingDeleteResponse(
                pairingCode = deleted.pairingCode,
                status = "DELETED",
            ),
        )
    }
}

private suspend fun ApplicationCall.requireSharedSecret(validator: SharedSecretValidator): Boolean {
    val actualSecret = request.header(SHARED_SECRET_HEADER)
    if (validator.isValid(actualSecret)) {
        return true
    }

    respondError(HttpStatusCode.Unauthorized, "INVALID_SHARED_SECRET", "Invalid sharedSecret.")
    return false
}

private fun validateRegisterRequest(
    request: PairingRegisterRequest,
    normalizedPlatform: String,
    validatedConfig: ValidatedConfig,
): String? {
    if (!isValidPairingCode(request.pairingCode.trim())) {
        return "INVALID_PAIRING_CODE"
    }
    if (!minecraftPlayerNameRegex.matches(request.minecraftPlayerName.trim())) {
        return "INVALID_MINECRAFT_PLAYER_NAME"
    }
    if (normalizedPlatform !in validatedConfig.enabledPlatforms) {
        return "INVALID_PLATFORM"
    }
    return null
}

private fun isValidPairingCode(pairingCode: String): Boolean =
    pairingCodeRegex.matches(pairingCode)

private fun parseMinecraftUuid(value: String): UUID? =
    runCatching {
        val trimmed = value.trim()
        if ('-' in trimmed) {
            UUID.fromString(trimmed)
        } else if (trimmed.length == 32) {
            UUID.fromString(
                "${trimmed.substring(0, 8)}-${trimmed.substring(8, 12)}-${trimmed.substring(12, 16)}-" +
                    "${trimmed.substring(16, 20)}-${trimmed.substring(20, 32)}",
            )
        } else {
            null
        }
    }.getOrNull()

private fun buildAuthorizeUrl(publicBaseUrl: String, session: PairingSession): String {
    val baseUrl = publicBaseUrl.trimEnd('/')
    val encodedCode = URLEncoder.encode(session.pairingCode, StandardCharsets.UTF_8)
    return "$baseUrl/oauth/${session.platform}/start?pairingCode=$encodedCode"
}

private fun PairingSession.toStatusResponse(): PairingStatusResponse =
    PairingStatusResponse(
        pairingCode = pairingCode,
        status = status.name,
        platform = platform,
        minecraftPlayerName = playerName,
        minecraftUuid = playerUuid.toString(),
        channelId = channelInfo?.channelId,
        channelName = channelInfo?.channelName,
        accessToken = token?.accessToken,
        refreshToken = token?.refreshToken,
        tokenType = token?.tokenType,
        scopes = token?.scopes?.sorted(),
        expiresAt = token?.expiresAt?.toString(),
    )
