package kr.meeor.mcstreamapi.authserver.pairing

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PairingService(
    private val store: InMemoryPairingStore,
    private val pairingExpireSeconds: Long,
    private val clock: Clock = Clock.systemUTC(),
    private val codeGenerator: PairingCodeGenerator = SecurePairingCodeGenerator(),
) {
    private val log = LoggerFactory.getLogger(PairingService::class.java)

    init {
        require(pairingExpireSeconds > 0) {
            "pairingExpireSeconds must be positive."
        }
    }

    fun createPending(
        platform: String,
        playerUuid: UUID,
        playerName: String,
        pairingCode: String = generateUniqueCode(),
    ): PairingSession {
        val now = clock.instant()
        val session = PairingSession(
            pairingCode = pairingCode,
            platform = platform.lowercase(),
            playerUuid = playerUuid,
            playerName = playerName,
            status = PairingStatus.PENDING,
            createdAt = now,
            expiresAt = now.plus(Duration.ofSeconds(pairingExpireSeconds)),
        )
        return store.create(session).also {
            log.info(
                "Pairing created platform={} code={} player={} expiresAt={}",
                it.platform,
                it.pairingCode.maskCode(),
                it.playerName,
                it.expiresAt,
            )
        }
    }

    fun get(pairingCode: String): PairingSession =
        store.update(pairingCode) { current ->
            current.expireIfNeeded(clock.instant())
        }

    fun poll(pairingCode: String): PairingSession =
        store.updateWithResult(pairingCode) { current ->
            val active = current.expireIfNeeded(clock.instant())
            if (active.status != PairingStatus.AUTHORIZED) {
                active to active
            } else {
                val consumed = active.copy(
                    status = PairingStatus.CONSUMED,
                    consumedAt = clock.instant(),
                    token = null,
                )
                log.info(
                    "Pairing consumed platform={} code={} player={}",
                    consumed.platform,
                    consumed.pairingCode.maskCode(),
                    consumed.playerName,
                )
                active to consumed
            }
        }

    fun authorize(
        pairingCode: String,
        channelInfo: ChannelInfo,
        token: OAuthToken,
    ): PairingSession =
        store.update(pairingCode) { current ->
            val active = current.expireIfNeeded(clock.instant())
            if (active.status != PairingStatus.PENDING) {
                throw PairingException.InvalidTransition(active.status, PairingStatus.AUTHORIZED)
            }
            active.copy(
                status = PairingStatus.AUTHORIZED,
                authorizedAt = clock.instant(),
                channelInfo = channelInfo,
                token = token,
            ).also {
                log.info(
                    "Pairing authorized platform={} code={} player={} channelId={}",
                    it.platform,
                    it.pairingCode.maskCode(),
                    it.playerName,
                    channelInfo.channelId.maskValue(),
                )
            }
        }

    fun consume(pairingCode: String): PairingSession =
        store.update(pairingCode) { current ->
            val active = current.expireIfNeeded(clock.instant())
            if (active.status != PairingStatus.AUTHORIZED) {
                throw PairingException.InvalidTransition(active.status, PairingStatus.CONSUMED)
            }
            active.copy(
                status = PairingStatus.CONSUMED,
                consumedAt = clock.instant(),
                token = null,
            ).also {
                log.info(
                    "Pairing consumed platform={} code={} player={}",
                    it.platform,
                    it.pairingCode.maskCode(),
                    it.playerName,
                )
            }
        }

    fun delete(pairingCode: String): PairingSession =
        (store.remove(pairingCode) ?: throw PairingException.NotFound(pairingCode)).also {
            log.info("Pairing deleted platform={} code={} player={}", it.platform, it.pairingCode.maskCode(), it.playerName)
        }

    fun fail(pairingCode: String, reason: String): PairingSession =
        store.update(pairingCode) { current ->
            val active = current.expireIfNeeded(clock.instant())
            if (active.isTerminal) {
                throw PairingException.InvalidTransition(active.status, PairingStatus.FAILED)
            }
            active.copy(
                status = PairingStatus.FAILED,
                failedAt = clock.instant(),
                failureReason = reason.take(200),
                token = null,
            ).also {
                log.warn(
                    "Pairing failed platform={} code={} player={} reason={}",
                    it.platform,
                    it.pairingCode.maskCode(),
                    it.playerName,
                    it.failureReason,
                )
            }
        }

    fun expire(pairingCode: String): PairingSession =
        store.update(pairingCode) { current ->
            if (current.status != PairingStatus.PENDING && current.status != PairingStatus.AUTHORIZED) {
                throw PairingException.InvalidTransition(current.status, PairingStatus.EXPIRED)
            }
            current.expire(clock.instant()).also {
                log.info("Pairing expired platform={} code={} player={}", it.platform, it.pairingCode.maskCode(), it.playerName)
            }
        }

    fun expireDueSessions(): Int {
        var expiredCount = 0
        val now = clock.instant()
        store.snapshot()
            .filter { it.status == PairingStatus.PENDING || it.status == PairingStatus.AUTHORIZED }
            .filter { !now.isBefore(it.expiresAt) }
            .forEach { session ->
                runCatching {
                    store.update(session.pairingCode) { current ->
                        if (current.status == PairingStatus.PENDING || current.status == PairingStatus.AUTHORIZED) {
                            expiredCount++
                            current.expire(now).also {
                                log.info("Pairing expired platform={} code={} player={}", it.platform, it.pairingCode.maskCode(), it.playerName)
                            }
                        } else {
                            current
                        }
                    }
                }
            }
        return expiredCount
    }

    fun removeRetainedTerminalSessions(
        expiredRetainSeconds: Long,
        consumedRetainSeconds: Long,
        failedRetainSeconds: Long,
    ): Int {
        val now = clock.instant()
        return store.removeIf { session ->
            when (session.status) {
                PairingStatus.EXPIRED -> session.expiredAt?.isOlderThan(now, expiredRetainSeconds) == true
                PairingStatus.CONSUMED -> session.consumedAt?.isOlderThan(now, consumedRetainSeconds) == true
                PairingStatus.FAILED -> session.failedAt?.isOlderThan(now, failedRetainSeconds) == true
                else -> false
            }
        }
    }

    private fun PairingSession.expireIfNeeded(now: Instant): PairingSession =
        if (!now.isBefore(expiresAt) && (status == PairingStatus.PENDING || status == PairingStatus.AUTHORIZED)) {
            expire(now)
        } else {
            this
        }

    private fun PairingSession.expire(now: Instant): PairingSession =
        copy(
            status = PairingStatus.EXPIRED,
            expiredAt = now,
            token = null,
        )

    private fun generateUniqueCode(): String {
        repeat(10) {
            val code = codeGenerator.generate()
            if (store.find(code) == null) {
                return code
            }
        }
        throw PairingException.CodeGenerationFailed
    }
}

private fun Instant.isOlderThan(now: Instant, retainSeconds: Long): Boolean =
    !plus(Duration.ofSeconds(retainSeconds)).isAfter(now)

private fun String.maskCode(): String =
    if (length <= 4) "****" else take(2) + "****" + takeLast(2)

private fun String.maskValue(): String =
    if (length <= 6) "****" else take(3) + "****" + takeLast(3)

interface PairingCodeGenerator {
    fun generate(): String
}

class SecurePairingCodeGenerator(
    private val random: SecureRandom = SecureRandom(),
) : PairingCodeGenerator {
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()

    override fun generate(): String =
        buildString(8) {
            repeat(8) {
                append(alphabet[random.nextInt(alphabet.size)])
            }
        }
}

sealed class PairingException(message: String) : RuntimeException(message) {
    class NotFound(pairingCode: String) : PairingException("Pairing session not found. pairingCode=$pairingCode")

    class InvalidTransition(
        from: PairingStatus,
        to: PairingStatus,
    ) : PairingException("Invalid pairing transition. from=$from to=$to")

    object CodeGenerationFailed : PairingException("Failed to generate unique pairing code.")
}
