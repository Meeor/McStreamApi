package kr.meeor.mcstreamapi.authserver.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kr.meeor.mcstreamapi.authserver.security.LogMasker

fun Application.installExceptionHandling(logMasker: LogMasker) {
    val appLog = log

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(HttpStatusCode.NotFound, "NOT_FOUND", "Not found.")
        }

        exception<BadRequestException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, "BAD_REQUEST", cause.message ?: "Bad request.")
        }

        exception<NotFoundException> { call, _ ->
            call.respondError(HttpStatusCode.NotFound, "NOT_FOUND", "Not found.")
        }

        exception<Throwable> { call, cause ->
            val maskedMessage = logMasker.mask(cause.message ?: cause::class.simpleName.orEmpty())
            appLog.error(
                "Unhandled exception requestId={} path={} remote={} message={}",
                call.requestId(),
                call.request.path(),
                call.request.origin.remoteHost,
                maskedMessage,
            )

            call.respondError(
                HttpStatusCode.InternalServerError,
                "INTERNAL_ERROR",
                "Internal server error.",
            )
        }
    }
}

suspend fun io.ktor.server.application.ApplicationCall.respondError(
    status: HttpStatusCode,
    error: String,
    message: String,
) {
    respond(
        status,
        ErrorResponse(
            error = error,
            message = message,
            requestId = requestId(),
        ),
    )
}
