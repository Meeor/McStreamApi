package kr.meeor.mcstreamapi.authserver.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class OAuthStateServiceTest {
    private val baseTime: Instant = Instant.parse("2026-06-10T00:00:00Z")

    @Test
    fun `state can be consumed only once`() {
        val service = service(clockAt(baseTime))
        val state = service.create(pairingCode = "A7K29Q", platform = "soop")

        val first = service.consume(state.stateId, "soop")
        val second = service.consume(state.stateId, "soop")

        assertEquals(state, (first as OAuthStateResult.Valid).state)
        assertEquals("OAUTH_STATE_NOT_FOUND", (second as OAuthStateResult.Invalid).error)
    }

    @Test
    fun `expired state is invalid and removed`() {
        val clock = MutableClock(baseTime)
        val service = service(clock)
        val state = service.create(pairingCode = "A7K29Q", platform = "soop")

        clock.current = baseTime.plusSeconds(61)
        val result = service.consume(state.stateId, "soop")

        assertEquals("OAUTH_STATE_EXPIRED", (result as OAuthStateResult.Invalid).error)
        assertEquals("OAUTH_STATE_NOT_FOUND", (service.consume(state.stateId, "soop") as OAuthStateResult.Invalid).error)
    }

    @Test
    fun `platform mismatch is invalid and consumes state`() {
        val service = service(clockAt(baseTime))
        val state = service.create(pairingCode = "A7K29Q", platform = "soop")

        val mismatch = service.consume(state.stateId, "chzzk")
        val second = service.consume(state.stateId, "soop")

        assertEquals("OAUTH_STATE_PLATFORM_MISMATCH", (mismatch as OAuthStateResult.Invalid).error)
        assertEquals("OAUTH_STATE_NOT_FOUND", (second as OAuthStateResult.Invalid).error)
    }

    private fun service(clock: Clock): OAuthStateService =
        OAuthStateService(
            store = InMemoryStateStore(),
            stateExpireSeconds = 60,
            clock = clock,
            stateIdGenerator = FixedStateIdGenerator(),
        )

    private fun clockAt(instant: Instant): Clock =
        Clock.fixed(instant, ZoneOffset.UTC)

    private class FixedStateIdGenerator : StateIdGenerator {
        private var next = 0

        override fun generate(): String {
            next++
            return "state-$next"
        }
    }

    private class MutableClock(
        var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
