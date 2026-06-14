package kr.meeor.mcstreamapi.donation.chzzk

import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JavaChzzkSessionTransport(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : ChzzkSessionTransport {
    override fun open(
        sessionUrl: String,
        reconnectPolicy: ProviderReconnectPolicy,
        handler: ChzzkSessionHandler,
    ): ChzzkSession {
        val session = JavaChzzkSession(sessionUrl, reconnectPolicy, handler, httpClient)
        session.connect(attempt = 1)
        return session
    }
}

private class JavaChzzkSession(
    private val sessionUrl: String,
    private val reconnectPolicy: ProviderReconnectPolicy,
    private val handler: ChzzkSessionHandler,
    private val httpClient: HttpClient,
) : ChzzkSession {
    private val stopped = AtomicBoolean(false)
    @Volatile
    private var webSocket: WebSocket? = null

    fun connect(attempt: Int) {
        if (stopped.get()) {
            return
        }
        handler.onReconnecting(attempt)
        val uri = normalizeChzzkSessionUri(sessionUrl)
        httpClient.newWebSocketBuilder()
            .buildAsync(uri, Listener(attempt))
            .whenComplete { socket, throwable ->
                if (throwable != null) {
                    val reason = webSocketFailureReason(throwable, uri)
                    handler.onReconnectFailed(attempt, reason)
                    scheduleReconnect(attempt + 1, reason)
                } else {
                    webSocket = socket
                }
            }
    }

    override fun stop() {
        stopped.set(true)
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "McStreamApi session stop")
        webSocket = null
    }

    private fun scheduleReconnect(attempt: Int, reason: String?) {
        if (stopped.get()) {
            return
        }
        val delaySeconds = reconnectPolicy.delayForAttempt(attempt)
        handler.onReconnectScheduled(attempt, delaySeconds * 1000, reason)
        CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS).execute {
            connect(attempt)
        }
    }

    private inner class Listener(
        private val attempt: Int,
    ) : WebSocket.Listener {
        private val partialMessage = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            this@JavaChzzkSession.webSocket = webSocket
            handler.onSocketOpened(attempt)
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            partialMessage.append(data)
            if (last) {
                handleMessage(partialMessage.toString(), webSocket)
                partialMessage.setLength(0)
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
            handler.onDisconnected("code=$statusCode reason=$reason")
            if (!stopped.get()) {
                scheduleReconnect(attempt + 1, "close:$statusCode")
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            val reason = webSocketFailureReason(error, normalizeChzzkSessionUri(sessionUrl))
            handler.onDisconnected(reason)
            if (!stopped.get()) {
                scheduleReconnect(attempt + 1, reason)
            }
        }
    }

    private fun handleMessage(payload: String, webSocket: WebSocket? = null) {
        if (payload == ENGINE_IO_PING) {
            webSocket?.sendText(ENGINE_IO_PONG, true)
            return
        }
        if (payload.startsWith(ENGINE_IO_OPEN_PREFIX)) {
            webSocket?.sendText(SOCKET_IO_CONNECT, true)
            return
        }
        if (payload.startsWith(SOCKET_IO_EVENT_PREFIX)) {
            handleSocketIoEvent(payload.removePrefix(SOCKET_IO_EVENT_PREFIX))
            return
        }
        val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        handleJsonMessage(root)
    }

    private fun handleSocketIoEvent(payload: String) {
        val event = runCatching { Json.parseToJsonElement(payload) as? JsonArray }.getOrNull() ?: return
        val eventType = (event.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return
        val body = event.getOrNull(1) as? JsonObject ?: return
        when (eventType) {
            "SYSTEM" -> handleSystemMessage(body)
            "DONATION" -> handleDonationMessage(body)
        }
    }

    private fun handleJsonMessage(root: JsonObject) {
        handleSystemMessage(root)
        handleDonationMessage(root)
    }

    private fun handleSystemMessage(root: JsonObject) {
        val type = findString(root, "type")
        if (type == "connected") {
            findString(root, "sessionKey")?.let(handler::onConnected)
        }
    }

    private fun handleDonationMessage(root: JsonObject) {
        findDonationObject(root)?.let { donation ->
            handler.onDonation(
                ChzzkDonationDto(
                    donationType = findString(donation, "donationType"),
                    channelId = findString(donation, "channelId") ?: return,
                    donatorChannelId = findString(donation, "donatorChannelId"),
                    donatorNickname = findString(donation, "donatorNickname") ?: findString(donation, "nickname") ?: "unknown",
                    payAmount = findLong(donation, "payAmount") ?: findLong(donation, "amount") ?: return,
                    donationText = findString(donation, "donationText") ?: findString(donation, "message"),
                    messageTime = findLong(donation, "messageTime") ?: findLong(donation, "timestamp"),
                ),
            )
        }
    }

    private fun findDonationObject(element: JsonElement): JsonObject? {
        val obj = element as? JsonObject ?: return null
        if ((findLong(obj, "payAmount") != null || findLong(obj, "amount") != null) &&
            (findString(obj, "channelId") != null)
        ) {
            return obj
        }
        return obj.values.firstNotNullOfOrNull { findDonationObject(it) }
    }

    private fun findString(element: JsonElement, key: String): String? {
        val obj = element as? JsonObject ?: return null
        (obj[key] as? JsonPrimitive)?.contentOrNull?.let { return it }
        return obj.values.firstNotNullOfOrNull { findString(it, key) }
    }

    private fun findLong(element: JsonElement, key: String): Long? {
        val obj = element as? JsonObject ?: return null
        (obj[key] as? JsonPrimitive)?.longOrNull?.let { return it }
        (obj[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { return it }
        return obj.values.firstNotNullOfOrNull { findLong(it, key) }
    }

    companion object {
        private const val ENGINE_IO_OPEN_PREFIX = "0"
        private const val ENGINE_IO_PING = "2"
        private const val ENGINE_IO_PONG = "3"
        private const val SOCKET_IO_CONNECT = "40"
        private const val SOCKET_IO_EVENT_PREFIX = "42"
    }
}

internal fun normalizeChzzkSessionUri(sessionUrl: String): URI {
    val trimmed = sessionUrl.trim()
    val base = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "wss://" + trimmed.substring("https://".length)
        trimmed.startsWith("http://", ignoreCase = true) -> "ws://" + trimmed.substring("http://".length)
        else -> trimmed
    }
    val uri = URI.create(base)
    require(uri.scheme.equals("wss", ignoreCase = true) || uri.scheme.equals("ws", ignoreCase = true)) {
        "Unsupported CHZZK WebSocket URL scheme: ${uri.scheme ?: "missing"}"
    }
    require(!uri.host.isNullOrBlank()) {
        "Invalid CHZZK WebSocket URL host"
    }
    return uri.toSocketIoWebSocketUri()
}

internal fun webSocketFailureReason(throwable: Throwable, uri: URI): String {
    val root = throwable.rootCause()
    val message = root.message
        ?.replace(Regex("\\s+"), " ")
        ?.take(160)
        ?.takeIf { it.isNotBlank() }
    return buildString {
        append("type=").append(root.javaClass.simpleName)
        if (root !== throwable) {
            append(" wrappedBy=").append(throwable.javaClass.simpleName)
        }
        append(" target=").append(describeChzzkSessionUri(uri))
        val queryKeys = uri.queryKeys()
        if (queryKeys.isNotEmpty()) {
            append(" queryKeys=").append(queryKeys.joinToString(","))
        }
        if (queryKeys.isEmpty()) {
            append(" queryKeys=none")
        }
        if (message != null) {
            append(" message=").append(message)
        }
    }
}

internal fun describeChzzkSessionUrl(sessionUrl: String): String {
    return describeChzzkSessionUri(normalizeChzzkSessionUri(sessionUrl))
}

private fun describeChzzkSessionUri(uri: URI): String {
    return buildString {
        append(uri.scheme).append("://").append(uri.host)
        if (uri.port != -1) {
            append(":").append(uri.port)
        }
        append(uri.path.takeIf { it.isNotBlank() } ?: "/")
    }
}

private fun URI.queryKeys(): List<String> {
    return rawQuery
        ?.split("&")
        ?.mapNotNull { pair ->
            pair.substringBefore("=", missingDelimiterValue = pair)
                .takeIf { it.isNotBlank() }
        }
        .orEmpty()
}

private fun URI.toSocketIoWebSocketUri(): URI {
    val query = (rawQuery?.takeIf { it.isNotBlank() }?.plus("&") ?: "") + "EIO=3&transport=websocket"
    return URI.create(
        buildString {
            append(scheme).append("://")
            rawUserInfo?.let { append(it).append("@") }
            append(host)
            if (port != -1) {
                append(":").append(port)
            }
            append("/socket.io/?").append(query)
            rawFragment?.let { append("#").append(it) }
        },
    )
}

private fun Throwable.rootCause(): Throwable {
    var current = this
    val seen = mutableSetOf<Throwable>()
    while (current.cause != null && seen.add(current)) {
        current = current.cause!!
    }
    return current
}
