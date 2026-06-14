package kr.meeor.mcstreamapi.donation.soop

import kr.meeor.mcstreamapi.donation.ProviderReconnectPolicy
import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.token.OAuthToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JavaSoopSessionTransport(
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val baseUrl: String = DEFAULT_OPENAPI_BASE_URL,
    private val logger: PluginLogger? = null,
    private val receiveAdBalloons: Boolean = false,
    private val receiveVideoBalloons: Boolean = false,
) : SoopDonationSessionTransport {
    override fun open(
        token: OAuthToken,
        streamerId: String,
        streamerName: String,
        playerName: String,
        reconnectPolicy: ProviderReconnectPolicy,
        listener: (SoopDonationDto) -> Unit,
    ): SoopDonationSession {
        val session = JavaSoopSession(
            clientId = clientId,
            clientSecret = clientSecret,
            accessToken = token.accessToken,
            streamerId = streamerId,
            streamerName = streamerName,
            playerName = playerName,
            reconnectPolicy = reconnectPolicy,
            listener = listener,
            httpClient = httpClient,
            baseUrl = baseUrl,
            logger = logger,
            receiveAdBalloons = receiveAdBalloons,
            receiveVideoBalloons = receiveVideoBalloons,
        )
        session.connect(attempt = 1)
        return session
    }

    companion object {
        private const val DEFAULT_OPENAPI_BASE_URL = "https://openapi.sooplive.com"
    }
}

private class JavaSoopSession(
    private val clientId: String,
    private val clientSecret: String,
    private val accessToken: String,
    private val streamerId: String,
    private val streamerName: String,
    private val playerName: String,
    private val reconnectPolicy: ProviderReconnectPolicy,
    private val listener: (SoopDonationDto) -> Unit,
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val logger: PluginLogger?,
    private val receiveAdBalloons: Boolean,
    private val receiveVideoBalloons: Boolean,
) : SoopDonationSession {
    private val stopped = AtomicBoolean(false)

    @Volatile
    private var webSocket: WebSocket? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun connect(attempt: Int) {
        if (stopped.get()) {
            return
        }

        logger?.debug("§e[진행] SOOP 채팅 정보 요청 중: 플레이어=$playerName 채널=$streamerName 시도=$attempt")
        val room = runCatching { requestChatInfo() }.getOrElse { throwable ->
            logger?.warning("§c[실패] SOOP 채팅 정보 요청 실패: 플레이어=$playerName 채널=$streamerName 원인=${throwable.javaClass.simpleName}")
            scheduleReconnect(attempt + 1, throwable.javaClass.simpleName)
            return
        }
        logger?.debug("§e[진행] SOOP WebSocket 연결 시도: 플레이어=$playerName 채널=${room.bjNickname} 주소=${room.webSocketUri.host} 시도=$attempt")
        httpClient.newWebSocketBuilder()
            .subprotocols("chat")
            .buildAsync(room.webSocketUri, Listener(attempt, room))
            .whenComplete { socket, throwable ->
                if (throwable != null) {
                    logger?.warning("§c[실패] SOOP WebSocket 연결 실패: 플레이어=$playerName 채널=${room.bjNickname} 시도=$attempt 원인=${throwable.javaClass.simpleName}")
                    scheduleReconnect(attempt + 1, throwable.javaClass.simpleName)
                } else {
                    logger?.info("§a[성공] SOOP WebSocket 접속 완료: 플레이어=$playerName 채널=${room.bjNickname} 시도=$attempt")
                    webSocket = socket
                }
            }
    }

    override fun stop() {
        stopped.set(true)
        logger?.debug("§e[진행] SOOP WebSocket 연결 종료 요청: 플레이어=$playerName 채널=$streamerName")
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "McStreamApi session stop")
        webSocket = null
    }

    private fun requestChatInfo(): SoopChatRoom {
        val body = "access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/broad/access/chatinfo"))
            .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8;")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw SoopDonationProviderException("SOOP_INVALID_TOKEN")
        }
        if (response.statusCode() !in 200..299) {
            throw SoopDonationProviderException("SOOP_CHATINFO_FAILED")
        }

        val root = json.parseToJsonElement(response.body()).jsonObject()
        val result = root.int("result") ?: 0
        if (result <= 0) {
            throw SoopDonationProviderException("SOOP_CHATINFO_DENIED")
        }
        val data = root["data"] as? JsonArray ?: throw SoopDonationProviderException("SOOP_CHATINFO_MISSING")
        val room = data.firstOrNull() as? JsonObject ?: throw SoopDonationProviderException("SOOP_CHATINFO_MISSING")
        val chatIp = room.string("chat_ip") ?: throw SoopDonationProviderException("SOOP_CHATINFO_MISSING")
        val chatPort = room.int("chat_port") ?: throw SoopDonationProviderException("SOOP_CHATINFO_MISSING")
        val bjId = room.string("id") ?: streamerId
        return SoopChatRoom(
            bjId = bjId,
            bjNickname = room.string("nick") ?: streamerName,
            broadNo = room.string("broad_no").orEmpty(),
            chatNo = room.string("chat_no") ?: throw SoopDonationProviderException("SOOP_CHATINFO_MISSING"),
            ticket = room.string("key") ?: throw SoopDonationProviderException("SOOP_CHATINFO_MISSING"),
            webSocketUri = URI.create("wss://chat-${chatIp.toHexHost()}.sooplive.com:${chatPort + 1}/Websocket/$bjId"),
        )
    }

    private fun scheduleReconnect(attempt: Int, reason: String?) {
        if (stopped.get()) {
            return
        }
        val delaySeconds = reconnectPolicy.delayForAttempt(attempt)
        logger?.debug("§e[대기] SOOP 세션 재연결 예약: 플레이어=$playerName 채널=$streamerName 시도=$attempt 대기=${delaySeconds * 1000}ms 원인=${reason ?: "알 수 없음"}")
        CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS).execute {
            connect(attempt)
        }
    }

    private inner class Listener(
        private val attempt: Int,
        private val room: SoopChatRoom,
    ) : WebSocket.Listener {
        private val binaryMessage = mutableListOf<ByteBuffer>()

        override fun onOpen(webSocket: WebSocket) {
            this@JavaSoopSession.webSocket = webSocket
            logger?.debug("§e[진행] SOOP 세션 로그인 요청 중: 플레이어=$playerName 채널=${room.bjNickname}")
            webSocket.sendBinary(SoopPacketCodec.encode(SVC_SDK_LOGIN, listOf(room.ticket, GUEST_FLAG.toString())), true)
            webSocket.request(1)
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> {
            binaryMessage.add(data.asReadOnlyBuffer())
            if (last) {
                handlePacket(SoopPacketCodec.decode(binaryMessage.merge()))
                binaryMessage.clear()
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            if (last) {
                handlePacket(SoopPacketCodec.decode(ByteBuffer.wrap(data.toString().toByteArray(StandardCharsets.UTF_8))))
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
            logger?.warning("§c[끊김] SOOP 세션 연결 끊김: 플레이어=$playerName 채널=${room.bjNickname} 원인=code=$statusCode reason=$reason")
            if (!stopped.get()) {
                scheduleReconnect(attempt + 1, "close:$statusCode")
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            logger?.warning("§c[끊김] SOOP WebSocket 오류: 플레이어=$playerName 채널=${room.bjNickname} 원인=${error.javaClass.simpleName}")
            if (!stopped.get()) {
                scheduleReconnect(attempt + 1, error.javaClass.simpleName)
            }
        }

        private fun handlePacket(packet: SoopPacket) {
            if (packet.retCode < 0) {
                return
            }
            when (packet.serviceCode) {
                SVC_SDK_LOGIN -> {
                    logger?.debug("§e[진행] SOOP 채팅방 join 요청 중: 플레이어=$playerName 채널=${room.bjNickname}")
                    webSocket?.sendBinary(SoopPacketCodec.encode(SVC_JOINCH, joinFields(room)), true)
                    CompletableFuture.delayedExecutor(KEEPALIVE_SECONDS, TimeUnit.SECONDS).execute {
                        sendKeepAlive()
                    }
                }
                SVC_JOINCH -> {
                    logger?.info("§a[성공] SOOP 세션 연결 완료: 플레이어=$playerName 채널=${room.bjNickname}")
                }
                SVC_SENDBALLOON -> donationFrom(packet, bjId = packet.field(0), userId = packet.field(1), nickname = packet.field(2), count = packet.long(3))
                SVC_SENDBALLOONSUB -> donationFrom(packet, bjId = packet.field(1), userId = packet.field(3), nickname = packet.field(4), count = packet.long(5))
                SVC_VODBALLOON -> donationFrom(packet, bjId = packet.field(0), userId = packet.field(1), nickname = packet.field(2), count = packet.long(3))
                SVC_VODADCON -> if (receiveAdBalloons) {
                    donationFrom(packet, bjId = packet.field(0), userId = packet.field(1), nickname = packet.field(2), count = packet.long(3))
                }
                SVC_STATION_ADCON -> if (receiveAdBalloons) {
                    donationFrom(packet, bjId = packet.field(0), userId = packet.field(1), nickname = packet.field(2), count = packet.long(3))
                }
                SVC_ADCON_EFFECT -> if (receiveAdBalloons) {
                    donationFrom(packet, bjId = packet.field(1), userId = packet.field(2), nickname = packet.field(3), count = packet.long(9))
                }
                SVC_VIDEO_BALLOON -> if (receiveVideoBalloons) {
                    donationFrom(packet, bjId = packet.field(1), userId = packet.field(2), nickname = packet.field(3), count = packet.long(4))
                }
            }
        }

        private fun joinFields(room: SoopChatRoom): List<String> {
            return listOf(
                room.chatNo,
                room.ticket,
                "5",
                "",
                sdkLog(clientId, clientSecret) + sdkAddInfo(),
            )
        }

        private fun sendKeepAlive() {
            if (stopped.get()) {
                return
            }
            webSocket?.sendBinary(SoopPacketCodec.encode(SVC_KEEPALIVE, emptyList()), true)
            CompletableFuture.delayedExecutor(KEEPALIVE_SECONDS, TimeUnit.SECONDS).execute {
                sendKeepAlive()
            }
        }

        private fun donationFrom(packet: SoopPacket, bjId: String?, userId: String?, nickname: String?, count: Long?) {
            if (count == null || count <= 0) {
                return
            }
            listener(
                SoopDonationDto(
                    eventId = "soop:${packet.serviceCode}:${packet.fields.joinToString("|").sha256Short()}",
                    streamerId = bjId ?: room.bjId,
                    streamerName = room.bjNickname,
                    donatorName = nickname ?: userId ?: "unknown",
                    amount = count,
                    message = null,
                    occurredAtEpochSeconds = Instant.now().epochSecond,
                ),
            )
        }
    }

    companion object {
        private const val SVC_KEEPALIVE = 0
        private const val SVC_JOINCH = 2
        private const val SVC_SDK_LOGIN = 16
        private const val SVC_SENDBALLOON = 18
        private const val SVC_SENDBALLOONSUB = 33
        private const val SVC_VODBALLOON = 86
        private const val SVC_ADCON_EFFECT = 87
        private const val SVC_VODADCON = 103
        private const val SVC_VIDEO_BALLOON = 105
        private const val SVC_STATION_ADCON = 107
        private const val GUEST_FLAG = 16
        private const val KEEPALIVE_SECONDS = 60L
    }
}

private data class SoopChatRoom(
    val bjId: String,
    val bjNickname: String,
    val broadNo: String,
    val chatNo: String,
    val ticket: String,
    val webSocketUri: URI,
)

private data class SoopPacket(
    val serviceCode: Int,
    val retCode: Int,
    val fields: List<String>,
) {
    fun field(index: Int): String? = fields.getOrNull(index)?.takeIf { it.isNotBlank() }

    fun long(index: Int): Long? = field(index)?.toLongOrNull()
}

private object SoopPacketCodec {
    private val separator = byteArrayOf(12)

    fun encode(serviceCode: Int, fields: List<String>): ByteBuffer {
        val body = ByteArrayBuilder()
        body.append(separator)
        fields.forEach { field ->
            body.append(field.toByteArray(StandardCharsets.UTF_8))
            body.append(separator)
        }
        val bodyBytes = body.toByteArray()
        val header = buildString {
            append(27.toChar())
            append(9.toChar())
            append(serviceCode.toString().padStart(4, '0'))
            append(bodyBytes.size.toString().padStart(6, '0'))
            append("00")
        }.toByteArray(StandardCharsets.US_ASCII)
        return ByteBuffer.wrap(header + bodyBytes)
    }

    fun decode(buffer: ByteBuffer): SoopPacket {
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (bytes.size < 14) {
            return SoopPacket(serviceCode = -1, retCode = -1, fields = emptyList())
        }
        val header = String(bytes, 0, 14, StandardCharsets.US_ASCII)
        val body = bytes.copyOfRange(14, bytes.size)
        return SoopPacket(
            serviceCode = header.substring(2, 6).toIntOrNull() ?: -1,
            retCode = header.substring(12, 14).toIntOrNull() ?: -1,
            fields = body.splitBySeparator().drop(1),
        )
    }
}

private class ByteArrayBuilder {
    private val bytes = mutableListOf<Byte>()

    fun append(value: ByteArray) {
        value.forEach(bytes::add)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}

private fun List<ByteBuffer>.merge(): ByteBuffer {
    val size = sumOf { it.remaining() }
    val merged = ByteBuffer.allocate(size)
    forEach { merged.put(it) }
    merged.flip()
    return merged
}

private fun ByteArray.splitBySeparator(): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    for (index in indices) {
        if (this[index] == 12.toByte()) {
            parts += String(this, start, index - start, StandardCharsets.UTF_8)
            start = index + 1
        }
    }
    if (start <= size) {
        parts += String(this, start, size - start, StandardCharsets.UTF_8)
    }
    return parts
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
}

private fun kotlinx.serialization.json.JsonElement.jsonObject(): JsonObject {
    return this as? JsonObject ?: throw SoopDonationProviderException("SOOP_INVALID_RESPONSE")
}

private fun String.toHexHost(): String {
    return split(".")
        .map { part -> part.toIntOrNull()?.toString(16)?.padStart(2, '0') ?: "00" }
        .joinToString("")
        .uppercase()
}

private fun sdkLog(clientId: String, clientSecret: String): String {
    val itemSeparator = 6.toChar()
    val keyValueSeparator = 38.toChar()
    val valueSeparator = 61.toChar()
    val recordSeparator = 17.toChar()
    val recordEnd = 18.toChar()
    return buildString {
        append("log")
        append(recordSeparator)
        append(itemSeparator).append(keyValueSeparator).append(itemSeparator)
        append("is_chat_sdk")
        append(itemSeparator).append(valueSeparator).append(itemSeparator)
        append("true")
        append(itemSeparator).append(keyValueSeparator).append(itemSeparator)
        append("sdk_client_id")
        append(itemSeparator).append(valueSeparator).append(itemSeparator)
        append(clientId)
        append(itemSeparator).append(keyValueSeparator).append(itemSeparator)
        append("sdk_client_secret")
        append(itemSeparator).append(valueSeparator).append(itemSeparator)
        append(clientSecret)
        append(recordEnd)
    }
}

private fun sdkAddInfo(): String {
    return "access_system${17.toChar()}html5${18.toChar()}"
}

private fun String.sha256Short(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(StandardCharsets.UTF_8))
    return digest.take(8).joinToString("") { "%02x".format(it) }
}
