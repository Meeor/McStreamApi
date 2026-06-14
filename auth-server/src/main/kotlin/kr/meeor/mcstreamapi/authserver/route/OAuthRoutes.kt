package kr.meeor.mcstreamapi.authserver.route

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProviderException
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProviderRegistry
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateResult
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateService
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateException
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

        val state = try {
            if (provider.supportsState) {
                stateService.create(pairingCode = pairingCode, platform = platform)
            } else {
                stateService.createExclusiveForPlatform(pairingCode = pairingCode, platform = platform)
            }
        } catch (exception: OAuthStateException) {
            call.respondFailurePage(HttpStatusCode.Conflict, exception.error, exception.message)
            return@get
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

        val stateResult = if (stateId.isBlank()) {
            if (provider.supportsState) {
                call.respondFailurePage(HttpStatusCode.BadRequest, "OAUTH_STATE_REQUIRED", "OAuth state가 필요합니다.")
                return@get
            }
            stateService.consumeExclusiveForPlatform(platform)
        } else {
            stateService.consume(stateId, platform)
        }
        if (stateResult is OAuthStateResult.Invalid) {
            call.respondFailurePage(HttpStatusCode.BadRequest, stateResult.error, stateResult.message)
            return@get
        }
        val state = (stateResult as OAuthStateResult.Valid).state

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
}

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
