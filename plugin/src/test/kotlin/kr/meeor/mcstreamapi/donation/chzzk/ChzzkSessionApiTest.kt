package kr.meeor.mcstreamapi.donation.chzzk

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChzzkSessionApiTest {
    @Test
    fun `creates user session and subscribes donation event`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<String>()
        server.createContext("/open/v1/sessions/auth") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestHeaders.getFirst("Authorization")}")
            val body = """{"url":"https://ssio.example.com?auth=token"}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.createContext("/open/v1/sessions/events/subscribe/donation") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI.path} ${exchange.requestHeaders.getFirst("Authorization")}")
            exchange.sendResponseHeaders(204, -1)
        }
        server.start()

        try {
            val api = ChzzkSessionApi(baseUrl = "http://127.0.0.1:${server.address.port}")

            val url = api.createUserSession("access-token").getOrThrow()
            api.subscribeDonation("access-token", "session-key").getOrThrow()

            assertEquals("https://ssio.example.com?auth=token", url)
            assertTrue(requests.contains("GET /open/v1/sessions/auth Bearer access-token"))
            assertTrue(requests.contains("POST /open/v1/sessions/events/subscribe/donation Bearer access-token"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `maps rate limit response`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/open/v1/sessions/auth") { exchange ->
            exchange.sendResponseHeaders(429, -1)
        }
        server.start()

        try {
            val api = ChzzkSessionApi(baseUrl = "http://127.0.0.1:${server.address.port}")
            val result = api.createUserSession("access-token")

            assertEquals("CHZZK_RATE_LIMITED", (result.exceptionOrNull() as ChzzkDonationProviderException).code)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `creates user session from wrapped url response`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/open/v1/sessions/auth") { exchange ->
            val body = """{"content":{"url":"https://ssio.example.com?auth=wrapped"}}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()

        try {
            val api = ChzzkSessionApi(baseUrl = "http://127.0.0.1:${server.address.port}")

            val url = api.createUserSession("access-token").getOrThrow()

            assertEquals("https://ssio.example.com?auth=wrapped", url)
        } finally {
            server.stop(0)
        }
    }
}
