package kr.meeor.mcstreamapi.donation.chzzk

import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.logging.PluginLogger
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
    private val logger: PluginLogger? = null,
) : ChzzkSessionTransport {
    override fun open(
        sessionUrl: String,
        reconnectPolicy: ProviderReconnectPolicy,
        handler: ChzzkSessionHandler,
    ): ChzzkSession {
        val session = JavaChzzkSession(sessionUrl, reconnectPolicy, handler, httpClient, logger)
        session.connect(attempt = 1)
        return session
    }
}

private class JavaChzzkSession(
    private val sessionUrl: String,
    private val reconnectPolicy: ProviderReconnectPolicy,
    private val handler: ChzzkSessionHandler,
    private val httpClient: HttpClient,
    private val logger: PluginLogger?,
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
        logger?.debug("§e[수신] CHZZK WebSocket payload 수신: preview=${payload.previewValue()}")
        if (payload == ENGINE_IO_PING) {
            logger?.debug("§e[진행] CHZZK Engine.IO ping 수신: pong 응답")
            webSocket?.sendText(ENGINE_IO_PONG, true)
            return
        }
        if (payload.startsWith(ENGINE_IO_OPEN_PREFIX)) {
            logger?.debug("§e[진행] CHZZK Engine.IO open 수신: Socket.IO connect 요청")
            webSocket?.sendText(SOCKET_IO_CONNECT, true)
            return
        }
        if (payload.startsWith(SOCKET_IO_EVENT_PREFIX)) {
            handleSocketIoEvent(payload.removePrefix(SOCKET_IO_EVENT_PREFIX))
            return
        }
        val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: run {
            logger?.debug("§e[대기] CHZZK 미처리 payload: 원인=JSON_PARSE_FAILED preview=${payload.previewValue()}")
            return
        }
        handleJsonMessage(root)
    }

    private fun handleSocketIoEvent(payload: String) {
        val event = runCatching { Json.parseToJsonElement(payload) as? JsonArray }.getOrNull() ?: run {
            logger?.debug("§e[대기] CHZZK Socket.IO 이벤트 무시: 원인=JSON_ARRAY_PARSE_FAILED preview=${payload.previewValue()}")
            return
        }
        val eventType = (event.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: run {
            logger?.debug("§e[대기] CHZZK Socket.IO 이벤트 무시: 원인=EVENT_TYPE_MISSING preview=${payload.previewValue()}")
            return
        }
        val body = event.getOrNull(1)?.asObjectPayload() ?: run {
            logger?.debug("§e[대기] CHZZK Socket.IO 이벤트 무시: eventType=$eventType 원인=BODY_MISSING preview=${payload.previewValue()}")
            return
        }
        logger?.debug(
            "§e[수신] CHZZK Socket.IO 이벤트 수신: eventType=$eventType keys=${body.keys.joinToString(",")} " +
                "preview=${body.previewJson()}",
        )
        when (eventType) {
            "SYSTEM" -> handleSystemMessage(body)
            "DONATION" -> if (!handleDonationMessage(body)) {
                handleUnknownEvent(eventType, body)
            }
            else -> handleUnknownEvent(eventType, body)
        }
    }

    private fun handleJsonMessage(root: JsonObject) {
        logger?.debug("§e[수신] CHZZK JSON payload 수신: keys=${root.keys.joinToString(",")} preview=${root.previewJson()}")
        handleSystemMessage(root)
        if (!handleDonationMessage(root)) {
            handleUnknownEvent("JSON", root)
        }
    }

    private fun handleSystemMessage(root: JsonObject) {
        val type = findString(root, "type")
        if (type == "connected") {
            findString(root, "sessionKey")?.let(handler::onConnected)
        }
    }

    private fun handleDonationMessage(root: JsonObject): Boolean {
        val donation = findDonationObject(root) ?: return false
        val channelId = findString(donation, "channelId")
        val amount = findLong(donation, "payAmount") ?: findLong(donation, "amount")
        if (channelId == null || amount == null) {
            logger?.warning(
                "§e[후보] CHZZK 후원 후보 payload: channelId=${channelId ?: "missing"} amount=${amount ?: "missing"} " +
                    "keys=${donation.keys.joinToString(",")} preview=${donation.previewJson()}",
            )
            return false
        }

        val donatorName = findString(donation, "donatorNickname") ?: findString(donation, "nickname") ?: "unknown"
        logger?.info("§a[후원] CHZZK 후원 감지: 후원자=$donatorName 금액=$amount")
        handler.onDonation(
            ChzzkDonationDto(
                donationType = findString(donation, "donationType"),
                channelId = channelId,
                donatorChannelId = findString(donation, "donatorChannelId"),
                donatorNickname = donatorName,
                payAmount = amount,
                donationText = findString(donation, "donationText") ?: findString(donation, "message"),
                messageTime = findLong(donation, "messageTime") ?: findLong(donation, "timestamp"),
            ),
        )
        return true
    }

    private fun handleUnknownEvent(eventType: String, body: JsonObject) {
        val amountCandidates = body.amountCandidates()
        if (amountCandidates.isNotEmpty()) {
            logger?.warning(
                "§e[후보] CHZZK 금액 후보 이벤트: eventType=$eventType candidates=${amountCandidates.joinToString(",")} " +
                    "keys=${body.keys.joinToString(",")} preview=${body.previewJson()}",
            )
            return
        }

        logger?.debug(
            "§e[대기] CHZZK 미처리 이벤트: eventType=$eventType keys=${body.keys.joinToString(",")} " +
                "preview=${body.previewJson()}",
        )
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

private fun JsonObject.previewJson(maxLength: Int = 320): String {
    return toString().previewValue(maxLength)
}

private fun JsonElement.asObjectPayload(): JsonObject? {
    (this as? JsonObject)?.let { return it }
    val content = (this as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching { Json.parseToJsonElement(content).jsonObject }.getOrNull()
}

private fun String.previewValue(maxLength: Int = 320): String {
    val normalized = replace(Regex("\\s+"), " ")
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength) + "..."
    }
}

private fun JsonObject.amountCandidates(): List<String> {
    val candidates = mutableListOf<String>()
    collectAmountCandidates(prefix = "", element = this, candidates = candidates)
    return candidates.take(12)
}

private fun collectAmountCandidates(prefix: String, element: JsonElement, candidates: MutableList<String>) {
    when (element) {
        is JsonObject -> element.forEach { (key, value) ->
            val path = if (prefix.isBlank()) key else "$prefix.$key"
            collectAmountCandidates(path, value, candidates)
        }
        is JsonArray -> element.forEachIndexed { index, value ->
            collectAmountCandidates("$prefix[$index]", value, candidates)
        }
        is JsonPrimitive -> {
            val number = element.longOrNull ?: element.contentOrNull?.toLongOrNull() ?: return
            if (number in 1..100_000_000) {
                candidates.add("$prefix=$number")
            }
        }
    }
}
