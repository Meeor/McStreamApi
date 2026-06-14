package kr.meeor.mcstreamapi.authserver.oauth

import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class OAuthState(
    val stateId: String,
    val pairingCode: String,
    val platform: String,
    val createdAt: Instant,
    val expiresAt: Instant,
)

class InMemoryStateStore {
    private val states = LinkedHashMap<String, OAuthState>()

    fun create(state: OAuthState): OAuthState =
        synchronized(states) {
            require(!states.containsKey(state.stateId)) {
                "OAuth state already exists."
            }
            states[state.stateId] = state
            state
        }

    fun createExclusiveForPlatform(state: OAuthState, now: Instant): OAuthState =
        synchronized(states) {
            removeExpiredLocked(now)
            val hasActive = states.values.any { it.platform == state.platform }
            require(!hasActive) {
                "OAuth state already exists for platform."
            }
            require(!states.containsKey(state.stateId)) {
                "OAuth state already exists."
            }
            states[state.stateId] = state
            state
        }

    fun consume(stateId: String): OAuthState? =
        synchronized(states) {
            states.remove(stateId)
        }

    fun consumeExclusiveForPlatform(platform: String, now: Instant): ExclusiveStateConsumeResult =
        synchronized(states) {
            removeExpiredLocked(now)
            val matches = states.values.filter { it.platform == platform }
            when (matches.size) {
                0 -> ExclusiveStateConsumeResult.NotFound
                1 -> {
                    states.remove(matches.single().stateId)
                    ExclusiveStateConsumeResult.Valid(matches.single())
                }
                else -> ExclusiveStateConsumeResult.Ambiguous
            }
        }

    fun removeExpired(now: Instant): Int =
        synchronized(states) {
            removeExpiredLocked(now)
        }

    private fun removeExpiredLocked(now: Instant): Int {
        val expired = states.values
            .filter { !now.isBefore(it.expiresAt) }
            .map { it.stateId }
        expired.forEach(states::remove)
        return expired.size
    }
}

class OAuthStateService(
    private val store: InMemoryStateStore,
    private val stateExpireSeconds: Long,
    private val clock: Clock = Clock.systemUTC(),
    private val stateIdGenerator: StateIdGenerator = SecureStateIdGenerator(),
) {
    init {
        require(stateExpireSeconds > 0) {
            "stateExpireSeconds must be positive."
        }
    }

    fun create(pairingCode: String, platform: String): OAuthState {
        val now = clock.instant()
        return store.create(
            OAuthState(
                stateId = stateIdGenerator.generate(),
                pairingCode = pairingCode,
                platform = platform.lowercase(),
                createdAt = now,
                expiresAt = now.plus(Duration.ofSeconds(stateExpireSeconds)),
            ),
        )
    }

    fun createExclusiveForPlatform(pairingCode: String, platform: String): OAuthState {
        val now = clock.instant()
        val normalizedPlatform = platform.lowercase()
        return try {
            store.createExclusiveForPlatform(
                OAuthState(
                    stateId = stateIdGenerator.generate(),
                    pairingCode = pairingCode,
                    platform = normalizedPlatform,
                    createdAt = now,
                    expiresAt = now.plus(Duration.ofSeconds(stateExpireSeconds)),
                ),
                now,
            )
        } catch (exception: IllegalArgumentException) {
            throw OAuthStateException("OAUTH_STATE_ALREADY_PENDING", "Another OAuth request is already pending for this platform.")
        }
    }

    fun consume(stateId: String, platform: String): OAuthStateResult {
        val state = store.consume(stateId)
            ?: return OAuthStateResult.Invalid("OAUTH_STATE_NOT_FOUND", "OAuth state was not found.")
        val now = clock.instant()
        if (!now.isBefore(state.expiresAt)) {
            return OAuthStateResult.Invalid("OAUTH_STATE_EXPIRED", "OAuth state expired.")
        }
        if (state.platform != platform.lowercase()) {
            return OAuthStateResult.Invalid("OAUTH_STATE_PLATFORM_MISMATCH", "OAuth state platform mismatch.")
        }
        return OAuthStateResult.Valid(state)
    }

    fun consumeExclusiveForPlatform(platform: String): OAuthStateResult {
        return when (val result = store.consumeExclusiveForPlatform(platform.lowercase(), clock.instant())) {
            ExclusiveStateConsumeResult.NotFound ->
                OAuthStateResult.Invalid("OAUTH_STATE_NOT_FOUND", "OAuth state was not found.")
            ExclusiveStateConsumeResult.Ambiguous ->
                OAuthStateResult.Invalid("OAUTH_STATE_AMBIGUOUS", "Multiple OAuth states are pending for this platform.")
            is ExclusiveStateConsumeResult.Valid ->
                OAuthStateResult.Valid(result.state)
        }
    }

    fun removeExpired(): Int = store.removeExpired(clock.instant())
}

class OAuthStateException(
    val error: String,
    override val message: String,
) : RuntimeException(message)

sealed class ExclusiveStateConsumeResult {
    data object NotFound : ExclusiveStateConsumeResult()

    data object Ambiguous : ExclusiveStateConsumeResult()

    data class Valid(val state: OAuthState) : ExclusiveStateConsumeResult()
}

sealed class OAuthStateResult {
    data class Valid(val state: OAuthState) : OAuthStateResult()

    data class Invalid(
        val error: String,
        val message: String,
    ) : OAuthStateResult()
}

interface StateIdGenerator {
    fun generate(): String
}

class SecureStateIdGenerator(
    private val random: SecureRandom = SecureRandom(),
) : StateIdGenerator {
    override fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
