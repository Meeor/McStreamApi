package kr.meeor.mcstreamapi.auth

import com.sun.net.httpserver.HttpServer
import kr.meeor.mcstreamapi.config.PluginAuthConfig
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthClientTest {
    @Test
    fun `pairing status treats null scopes as empty list`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mca/api/pairing/ABCDEFGH") { exchange ->
            val response = """
                {
                  "pairingCode": "ABCDEFGH",
                  "status": "PENDING",
                  "platform": "chzzk",
                  "minecraftUuid": "00000000-0000-0000-0000-000000000001",
                  "scopes": null
                }
            """.trimIndent()

            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()

        try {
            val client = JavaAuthClient(
                PluginAuthConfig(
                    serverBaseUrl = "http://127.0.0.1:${server.address.port}/mca",
                    sharedSecret = "12345678901234567890123456789012",
                    pollingIntervalSeconds = 3,
                    pairingTimeoutSeconds = 600,
                ),
            )

            val result = client.getPairing("ABCDEFGH")

            assertTrue(result.isSuccess)
            assertEquals(emptyList(), result.getOrThrow().scopes)
        } finally {
            server.stop(0)
        }
    }
}
