package kr.meeor.mcstreamapi.authserver.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PairingServiceTest {
    private val baseTime: Instant = Instant.parse("2026-06-10T00:00:00Z")

    @Test
    fun `authorized session can be consumed and token is removed`() {
        val service = service(clockAt(baseTime))
        val pending = service.createPending("soop", UUID.randomUUID(), "Rion")
        val authorized = service.authorize(pending.pairingCode, channelInfo(), token())

        assertEquals(PairingStatus.AUTHORIZED, authorized.status)
        assertNotNull(authorized.token)

        val consumed = service.consume(pending.pairingCode)

        assertEquals(PairingStatus.CONSUMED, consumed.status)
        assertNull(consumed.token)
        assertNotNull(consumed.consumedAt)
    }

    @Test
    fun `poll returns authorized token only once`() {
        val service = service(clockAt(baseTime))
        val pending = service.createPending("soop", UUID.randomUUID(), "Rion")

        service.authorize(pending.pairingCode, channelInfo(), token())

        val firstPoll = service.poll(pending.pairingCode)
        val secondPoll = service.poll(pending.pairingCode)

        assertEquals(PairingStatus.AUTHORIZED, firstPoll.status)
        assertNotNull(firstPoll.token)
        assertEquals(PairingStatus.CONSUMED, secondPoll.status)
        assertNull(secondPoll.token)
    }

    @Test
    fun `pending session cannot be consumed`() {
        val service = service(clockAt(baseTime))
        val pending = service.createPending("soop", UUID.randomUUID(), "Rion")

        assertFailsWith<PairingException.InvalidTransition> {
            service.consume(pending.pairingCode)
        }
    }

    @Test
    fun `expired session removes authorized token`() {
        val clock = MutableClock(baseTime)
        val service = service(clock)
        val pending = service.createPending("soop", UUID.randomUUID(), "Rion")

        service.authorize(pending.pairingCode, channelInfo(), token())
        clock.current = baseTime.plusSeconds(61)

        val expired = service.expire(pending.pairingCode)

        assertEquals(PairingStatus.EXPIRED, expired.status)
        assertNull(expired.token)
        assertNotNull(expired.expiredAt)
    }

    @Test
    fun `failed session removes token and blocks later consume`() {
        val service = service(clockAt(baseTime))
        val pending = service.createPending("soop", UUID.randomUUID(), "Rion")

        service.authorize(pending.pairingCode, channelInfo(), token())
        val failed = service.fail(pending.pairingCode, "oauth_denied")

        assertEquals(PairingStatus.FAILED, failed.status)
        assertNull(failed.token)

        assertFailsWith<PairingException.InvalidTransition> {
            service.consume(pending.pairingCode)
        }
    }

    @Test
    fun `expired pending session cannot be authorized`() {
        val clock = MutableClock(baseTime)
        val service = service(clock)
        val pending = service.createPending("soop", UUID.randomUUID(), "Rion")

        clock.current = baseTime.plusSeconds(61)

        assertFailsWith<PairingException.InvalidTransition> {
            service.authorize(pending.pairingCode, channelInfo(), token())
        }

        val stored = service.expire(pending.pairingCode)
        assertEquals(PairingStatus.EXPIRED, stored.status)
        assertNull(stored.token)
    }

    @Test
    fun `terminal sessions are removed after retain window`() {
        val clock = MutableClock(baseTime)
        val service = service(clock)
        val consumed = service.createPending("soop", UUID.randomUUID(), "Rion")
        val failed = service.createPending("soop", UUID.randomUUID(), "Steve")
        val expired = service.createPending("soop", UUID.randomUUID(), "Alex")

        service.authorize(consumed.pairingCode, channelInfo(), token())
        service.consume(consumed.pairingCode)
        service.fail(failed.pairingCode, "oauth_denied")
        clock.current = baseTime.plusSeconds(61)
        service.expire(expired.pairingCode)

        clock.current = baseTime.plusSeconds(700)
        val removed = service.removeRetainedTerminalSessions(
            expiredRetainSeconds = 300,
            consumedRetainSeconds = 60,
            failedRetainSeconds = 600,
        )

        assertEquals(3, removed)
        assertFailsWith<PairingException.NotFound> { service.get(consumed.pairingCode) }
        assertFailsWith<PairingException.NotFound> { service.get(failed.pairingCode) }
        assertFailsWith<PairingException.NotFound> { service.get(expired.pairingCode) }
    }

    private fun service(clock: Clock): PairingService =
        PairingService(
            store = InMemoryPairingStore(),
            pairingExpireSeconds = 60,
            clock = clock,
            codeGenerator = FixedPairingCodeGenerator(),
        )

    private fun token(): OAuthToken =
        OAuthToken(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "Bearer",
            scopes = setOf("donation.read"),
            expiresAt = baseTime.plusSeconds(3600),
        )

    private fun channelInfo(): ChannelInfo =
        ChannelInfo(
            platform = "soop",
            channelId = "channel-id",
            channelName = "channel-name",
        )

    private fun clockAt(instant: Instant): Clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    private class FixedPairingCodeGenerator : PairingCodeGenerator {
        private var next = 0

        override fun generate(): String {
            next++
            return "TEST%04d".format(next)
        }
    }

    private class MutableClock(
        var current: Instant,
    ) : Clock() {
        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
