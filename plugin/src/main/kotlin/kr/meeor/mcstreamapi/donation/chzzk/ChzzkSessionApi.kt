package kr.meeor.mcstreamapi.donation.chzzk

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class ChzzkSessionApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) {
    fun createUserSession(accessToken: String): Result<String> {
        return send(
            method = "GET",
            path = "/open/v1/sessions/auth",
            accessToken = accessToken,
            body = null,
        ).map { body ->
            findString(Json.parseToJsonElement(body), "url")
                ?: throw ChzzkDonationProviderException("CHZZK_SESSION_URL_MISSING")
        }
    }

    fun subscribeDonation(accessToken: String, sessionKey: String): Result<Unit> {
        return send(
            method = "POST",
            path = "/open/v1/sessions/events/subscribe/donation",
            accessToken = accessToken,
            body = """{"sessionKey":"$sessionKey"}""",
        ).map { }
    }

    private fun send(
        method: String,
        path: String,
        accessToken: String,
        body: String?,
    ): Result<String> {
        return runCatching {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl$path"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
            val request = if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build()
            }
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw ChzzkDonationProviderException(mapStatus(response.statusCode()))
            }
            response.body()
        }.recoverCatching { throwable ->
            if (throwable is ChzzkDonationProviderException) {
                throw throwable
            }
            throw ChzzkDonationProviderException("CHZZK_SESSION_API_UNREACHABLE")
        }
    }

    private fun mapStatus(statusCode: Int): String {
        return when (statusCode) {
            401 -> "CHZZK_INVALID_TOKEN"
            403 -> "CHZZK_SCOPE_DENIED"
            429 -> "CHZZK_RATE_LIMITED"
            else -> "CHZZK_SESSION_API_FAILED"
        }
    }

    private fun findString(element: JsonElement, key: String): String? {
        val obj = element as? JsonObject ?: return null
        (obj[key] as? JsonPrimitive)?.contentOrNull?.let { return it }
        return obj.values.firstNotNullOfOrNull { findString(it, key) }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://openapi.chzzk.naver.com"
    }
}
