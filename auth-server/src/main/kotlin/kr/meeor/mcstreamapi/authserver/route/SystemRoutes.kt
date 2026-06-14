package kr.meeor.mcstreamapi.authserver.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kr.meeor.mcstreamapi.authserver.BuildInfo
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.http.HealthResponse
import kr.meeor.mcstreamapi.authserver.http.ReadyResponse
import kr.meeor.mcstreamapi.authserver.http.respondError

fun Route.systemRoutes(validatedConfig: ValidatedConfig) {
    get("/health") {
        call.respond(
            HealthResponse(
                status = "UP",
                service = BuildInfo.SERVICE_NAME,
                version = BuildInfo.VERSION,
            ),
        )
    }

    get("/ready") {
        val enabledPlatforms = validatedConfig.enabledPlatforms.sorted()
        call.respond(
            ReadyResponse(
                status = if (enabledPlatforms.isEmpty()) "NOT_READY" else "READY",
                enabledPlatforms = enabledPlatforms,
            ),
        )
    }
}

fun Route.notFoundRoute() {
    get("/{path...}") {
        call.respondError(HttpStatusCode.NotFound, "NOT_FOUND", "Not found.")
    }
}
