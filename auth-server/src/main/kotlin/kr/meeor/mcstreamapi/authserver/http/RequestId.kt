package kr.meeor.mcstreamapi.authserver.http

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.util.AttributeKey
import java.util.UUID

val RequestIdKey: AttributeKey<String> = AttributeKey("RequestId")

fun Application.installRequestId() {
    install(
        createApplicationPlugin(name = "RequestIdPlugin") {
            onCall { call ->
                val requestId = call.request.header("X-Request-Id")
                    ?.takeIf { it.isValidRequestId() }
                    ?: UUID.randomUUID().toString()

                call.attributes.put(RequestIdKey, requestId)
                call.response.header("X-Request-Id", requestId)
            }
        },
    )
}

fun ApplicationCall.requestId(): String =
    attributes.getOrNull(RequestIdKey) ?: "unknown"

private fun String.isValidRequestId(): Boolean =
    length in 8..80 && all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
