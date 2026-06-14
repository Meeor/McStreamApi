package kr.meeor.mcstreamapi.authserver.ops

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.meeor.mcstreamapi.authserver.config.CleanupConfig
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateService
import kr.meeor.mcstreamapi.authserver.pairing.PairingService
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class CleanupJob(
    private val config: CleanupConfig,
    private val pairingService: PairingService,
    private val stateService: OAuthStateService,
) {
    private val log = LoggerFactory.getLogger(CleanupJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun start() {
        if (job != null) {
            return
        }

        job = scope.launch {
            log.info("Cleanup job started intervalSeconds={}", config.intervalSeconds)
            while (isActive) {
                runCleanup()
                delay(config.intervalSeconds.seconds)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope.cancel()
        log.info("Cleanup job stopped")
    }

    fun runCleanup() {
        runCatching {
            val expiredPairings = pairingService.expireDueSessions()
            val removedPairings = pairingService.removeRetainedTerminalSessions(
                expiredRetainSeconds = config.expiredSessionRetainSeconds,
                consumedRetainSeconds = config.consumedSessionRetainSeconds,
                failedRetainSeconds = config.failedSessionRetainSeconds,
            )
            val removedStates = stateService.removeExpired()

            if (expiredPairings > 0 || removedPairings > 0 || removedStates > 0) {
                log.info(
                    "Cleanup completed expiredPairings={} removedPairings={} removedStates={}",
                    expiredPairings,
                    removedPairings,
                    removedStates,
                )
            }
        }.onFailure { cause ->
            log.warn("Cleanup failed message={}", cause.message)
        }
    }
}
