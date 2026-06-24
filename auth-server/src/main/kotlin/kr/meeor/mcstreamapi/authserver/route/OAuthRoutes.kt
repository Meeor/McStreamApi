package kr.meeor.mcstreamapi.authserver.route

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProviderException
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProviderRegistry
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateResult
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateService
import kr.meeor.mcstreamapi.authserver.pairing.PairingException
import kr.meeor.mcstreamapi.authserver.pairing.PairingService
import kr.meeor.mcstreamapi.authserver.pairing.PairingStatus

fun Route.oauthRoutes(
    validatedConfig: ValidatedConfig,
    pairingService: PairingService,
    stateService: OAuthStateService,
    providerRegistry: OAuthProviderRegistry,
) {
    get("/oauth/{platform}/start") {
        val platform = call.parameters["platform"].orEmpty().lowercase()
        val pairingCode = call.request.queryParameters["pairingCode"].orEmpty()

        if (platform !in validatedConfig.enabledPlatforms) {
            call.respondFailurePage(HttpStatusCode.BadRequest, "INVALID_PLATFORM", "지원하지 않는 플랫폼입니다.")
            return@get
        }
        if (pairingCode.isBlank()) {
            call.respondFailurePage(HttpStatusCode.BadRequest, "PAIRING_CODE_REQUIRED", "pairingCode가 필요합니다.")
            return@get
        }

        val session = runCatching { pairingService.get(pairingCode) }
            .getOrElse { cause ->
                if (cause is PairingException.NotFound) {
                    call.respondFailurePage(HttpStatusCode.NotFound, "PAIRING_NOT_FOUND", "인증 요청을 찾을 수 없습니다.")
                    return@get
                }
                throw cause
            }

        if (session.status != PairingStatus.PENDING || session.platform != platform) {
            call.respondFailurePage(HttpStatusCode.Conflict, "PAIRING_NOT_PENDING", "인증 요청을 시작할 수 없습니다.")
            return@get
        }

        val provider = providerRegistry.find(platform)
            ?: run {
                pairingService.fail(pairingCode, "PROVIDER_NOT_IMPLEMENTED")
                call.respondFailurePage(HttpStatusCode.NotImplemented, "PROVIDER_NOT_IMPLEMENTED", "아직 구현되지 않은 플랫폼입니다.")
                return@get
            }

        val state = stateService.create(pairingCode = pairingCode, platform = platform)
        if (!provider.supportsState) {
            call.appendOAuthStateCookie(validatedConfig, platform, state.stateId)
        }
        call.respondRedirect(provider.buildAuthorizeUrl(state.stateId))
    }

    get("/oauth/{platform}/callback") {
        val platform = call.parameters["platform"].orEmpty().lowercase()
        val stateId = call.request.queryParameters["state"].orEmpty()
        val code = call.request.queryParameters["code"].orEmpty()
        val oauthError = call.request.queryParameters["error"]

        if (platform !in validatedConfig.enabledPlatforms) {
            call.respondFailurePage(HttpStatusCode.BadRequest, "INVALID_PLATFORM", "지원하지 않는 플랫폼입니다.")
            return@get
        }
        val provider = providerRegistry.find(platform)
            ?: run {
                call.respondFailurePage(HttpStatusCode.NotImplemented, "PROVIDER_NOT_IMPLEMENTED", "아직 구현되지 않은 플랫폼입니다.")
                return@get
            }

        val stateResult = call.resolveOAuthState(
            validatedConfig = validatedConfig,
            stateService = stateService,
            providerSupportsState = provider.supportsState,
            platform = platform,
            stateId = stateId,
            code = code,
        ) ?: return@get
        if (stateResult is OAuthStateResult.Invalid) {
            call.respondFailurePage(HttpStatusCode.BadRequest, stateResult.error, stateResult.message)
            return@get
        }
        val state = (stateResult as OAuthStateResult.Valid).state
        if (!provider.supportsState) {
            call.clearOAuthStateCookie(validatedConfig, platform)
        }

        if (!oauthError.isNullOrBlank()) {
            pairingService.fail(state.pairingCode, oauthError.take(200))
            call.respondFailurePage(HttpStatusCode.BadRequest, "OAUTH_DENIED", "OAuth 인증이 거부되었습니다.")
            return@get
        }
        if (code.isBlank()) {
            pairingService.fail(state.pairingCode, "OAUTH_CODE_REQUIRED")
            call.respondFailurePage(HttpStatusCode.BadRequest, "OAUTH_CODE_REQUIRED", "OAuth code가 필요합니다.")
            return@get
        }

        runCatching {
            val token = provider.exchangeCodeForToken(code, state.stateId)
            val channelInfo = provider.fetchChannelInfo(token.accessToken)
            pairingService.authorize(state.pairingCode, channelInfo, token)
        }.getOrElse { cause ->
            val errorCode = if (cause is OAuthProviderException) cause.errorCode else "OAUTH_CALLBACK_FAILED"
            pairingService.fail(state.pairingCode, errorCode)
            call.respondFailurePage(HttpStatusCode.BadGateway, errorCode, "OAuth callback 처리 중 오류가 발생했습니다.")
            return@get
        }

        call.respondText(
            text = successHtmlPage(),
            contentType = ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }

    post("/oauth/soop/callback") {
        val platform = "soop"
        val form = call.receiveParameters()
        val pairingCode = form["pairingCode"].orEmpty().trim().uppercase()
        val code = form["code"].orEmpty()

        if (platform !in validatedConfig.enabledPlatforms) {
            call.respondFailurePage(HttpStatusCode.BadRequest, "INVALID_PLATFORM", "지원하지 않는 플랫폼입니다.")
            return@post
        }
        if (pairingCode.isBlank()) {
            call.respondFailurePage(HttpStatusCode.BadRequest, "PAIRING_CODE_REQUIRED", "인증 코드가 필요합니다.")
            return@post
        }
        if (code.isBlank()) {
            call.respondFailurePage(HttpStatusCode.BadRequest, "OAUTH_CODE_REQUIRED", "OAuth code가 필요합니다.")
            return@post
        }

        val provider = providerRegistry.find(platform)
            ?: run {
                call.respondFailurePage(HttpStatusCode.NotImplemented, "PROVIDER_NOT_IMPLEMENTED", "아직 구현되지 않은 플랫폼입니다.")
                return@post
            }
        val stateResult = stateService.consumeForPairingCode(pairingCode, platform)
        if (stateResult is OAuthStateResult.Invalid) {
            call.respondFailurePage(HttpStatusCode.BadRequest, stateResult.error, stateResult.message)
            return@post
        }
        val state = (stateResult as OAuthStateResult.Valid).state
        call.clearOAuthStateCookie(validatedConfig, platform)

        runCatching {
            val session = pairingService.get(state.pairingCode)
            if (session.status != PairingStatus.PENDING || session.platform != platform) {
                throw PairingException.InvalidTransition(session.status, PairingStatus.AUTHORIZED)
            }
            val token = provider.exchangeCodeForToken(code, state.stateId)
            val channelInfo = provider.fetchChannelInfo(token.accessToken)
            pairingService.authorize(state.pairingCode, channelInfo, token)
        }.getOrElse { cause ->
            val errorCode = if (cause is OAuthProviderException) cause.errorCode else "OAUTH_CALLBACK_FAILED"
            pairingService.fail(state.pairingCode, errorCode)
            call.respondFailurePage(HttpStatusCode.BadGateway, errorCode, "OAuth callback 처리 중 오류가 발생했습니다.")
            return@post
        }

        call.respondText(
            text = successHtmlPage(),
            contentType = ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }
}

private suspend fun ApplicationCall.resolveOAuthState(
    validatedConfig: ValidatedConfig,
    stateService: OAuthStateService,
    providerSupportsState: Boolean,
    platform: String,
    stateId: String,
    code: String,
): OAuthStateResult? {
    if (stateId.isNotBlank()) {
        return stateService.consume(stateId, platform)
    }
    if (providerSupportsState) {
        respondFailurePage(HttpStatusCode.BadRequest, "OAUTH_STATE_REQUIRED", "OAuth state가 필요합니다.")
        return null
    }

    val cookieStateId = request.cookies[oauthStateCookieName(platform)].orEmpty()
    if (cookieStateId.isNotBlank()) {
        return stateService.consume(cookieStateId, platform)
    }
    if (code.isNotBlank() && platform == "soop") {
        respondText(
            text = soopPairingFallbackHtmlPage(code),
            contentType = ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
        return null
    }

    return stateService.consumeExclusiveForPlatform(platform)
}

private fun ApplicationCall.appendOAuthStateCookie(
    validatedConfig: ValidatedConfig,
    platform: String,
    stateId: String,
) {
    response.cookies.append(
        Cookie(
            name = oauthStateCookieName(platform),
            value = stateId,
            path = "/",
            maxAge = validatedConfig.config.security.stateExpireSeconds.toInt(),
            secure = !validatedConfig.config.server.allowInsecureLocalhost,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

private fun ApplicationCall.clearOAuthStateCookie(
    validatedConfig: ValidatedConfig,
    platform: String,
) {
    response.cookies.append(
        Cookie(
            name = oauthStateCookieName(platform),
            value = "",
            path = "/",
            maxAge = 0,
            secure = !validatedConfig.config.server.allowInsecureLocalhost,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

private fun oauthStateCookieName(platform: String): String =
    "mca_oauth_${platform.lowercase()}_state"

private suspend fun io.ktor.server.application.ApplicationCall.respondFailurePage(
    status: HttpStatusCode,
    error: String,
    message: String,
) {
    respondText(
        text = failureHtmlPage(error, message),
        contentType = ContentType.Text.Html,
        status = status,
    )
}
