package kr.meeor.mcstreamapi.donation.chzzk

import java.net.ConnectException
import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class JavaChzzkSessionTransportTest {
    @Test
    fun `normalizes https session url to wss websocket uri`() {
        val uri = normalizeChzzkSessionUri("https://ssio.example.com/path?auth=token")

        assertEquals("wss://ssio.example.com/socket.io/?auth=token&EIO=3&transport=websocket", uri.toString())
    }

    @Test
    fun `keeps wss session url as websocket uri`() {
        val uri = normalizeChzzkSessionUri("wss://ssio.example.com/path?auth=token")

        assertEquals("wss://ssio.example.com/socket.io/?auth=token&EIO=3&transport=websocket", uri.toString())
    }

    @Test
    fun `websocket failure reason unwraps completion exception and omits query token`() {
        val uri = normalizeChzzkSessionUri("wss://ssio.example.com/path?auth=token")
        val reason = webSocketFailureReason(CompletionException(ConnectException("connection refused")), uri)

        assertContains(reason, "type=ConnectException")
        assertContains(reason, "wrappedBy=CompletionException")
        assertContains(reason, "target=wss://ssio.example.com/socket.io/")
        assertContains(reason, "queryKeys=auth")
        assertContains(reason, "EIO")
        assertContains(reason, "transport")
        assert(!reason.contains("auth=token"))
    }
}
