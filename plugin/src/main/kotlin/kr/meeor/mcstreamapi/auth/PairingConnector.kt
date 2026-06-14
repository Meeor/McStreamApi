package kr.meeor.mcstreamapi.auth

import kr.meeor.mcstreamapi.command.McaCommandSender
import kr.meeor.mcstreamapi.config.PluginAuthConfig
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore
import java.time.Instant

class PairingConnector(
    private val authClientFactory: (PluginAuthConfig) -> AuthClient = { JavaAuthClient(it) },
    private val tokenStore: TokenStore,
    private val scheduler: PairingScheduler,
    private val codeGenerator: PairingCodeGenerator = PairingCodeGenerator(),
    private val onTokenSaved: (playerUuid: String, playerName: String, platform: String) -> Unit = { _, _, _ -> },
) {
    fun start(
        sender: McaCommandSender,
        platform: String,
        authConfig: PluginAuthConfig,
    ): PairingStartResult {
        val minecraftUuid = sender.uuid ?: return PairingStartResult.Failure("플레이어 UUID를 확인할 수 없습니다.")
        val pairingCode = codeGenerator.generate()
        val authClient = authClientFactory(authConfig)

        val registered = authClient.registerPairing(
            PairingRegisterCommand(
                pairingCode = pairingCode,
                platform = platform,
                minecraftPlayerName = sender.name,
                minecraftUuid = minecraftUuid,
            ),
        ).getOrElse { throwable ->
            return PairingStartResult.Failure(userMessage(throwable))
        }

        schedulePolling(
            sender = sender,
            platform = platform,
            minecraftUuid = minecraftUuid,
            authConfig = authConfig,
            authClient = authClient,
            pairingCode = pairingCode,
        )

        return PairingStartResult.Started(
            pairingCode = registered.pairingCode,
            authorizeUrl = registered.authorizeUrl,
        )
    }

    private fun schedulePolling(
        sender: McaCommandSender,
        platform: String,
        minecraftUuid: String,
        authConfig: PluginAuthConfig,
        authClient: AuthClient,
        pairingCode: String,
    ) {
        val startedAt = Instant.now()
        var task: CancellableTask? = null
        task = scheduler.repeatAsync(
            initialDelayTicks = authConfig.pollingIntervalSeconds * TICKS_PER_SECOND,
            periodTicks = authConfig.pollingIntervalSeconds * TICKS_PER_SECOND,
        ) {
            val elapsed = Instant.now().epochSecond - startedAt.epochSecond
            if (elapsed >= authConfig.pairingTimeoutSeconds) {
                task?.cancel()
                sender.notify("인증 시간이 만료되었습니다. 다시 시도해 주세요.")
                return@repeatAsync
            }

            val status = authClient.getPairing(pairingCode).getOrElse { throwable ->
                if ((throwable as? AuthClientException)?.code == "PAIRING_EXPIRED") {
                    task?.cancel()
                    sender.notify("인증 시간이 만료되었습니다. 다시 시도해 주세요.")
                }
                return@repeatAsync
            }

            if (status.status != "AUTHORIZED") {
                return@repeatAsync
            }

            if (status.minecraftUuid != minecraftUuid) {
                task?.cancel()
                sender.notify("인증 응답의 플레이어 UUID가 일치하지 않습니다.")
                return@repeatAsync
            }

            if (status.accessToken.isNullOrBlank() || status.refreshToken.isNullOrBlank()) {
                task?.cancel()
                sender.notify("AuthServer 인증 응답이 올바르지 않습니다.")
                return@repeatAsync
            }

            val saveResult = runCatching {
                tokenStore.save(
                    OAuthToken(
                        platform = status.platform ?: platform,
                        minecraftUuid = minecraftUuid,
                        minecraftPlayerName = sender.name,
                        accessToken = status.accessToken,
                        refreshToken = status.refreshToken,
                        tokenType = status.tokenType ?: "Bearer",
                        scope = status.scopes.joinToString(" ").ifBlank { null },
                        expiresAtEpochSeconds = status.expiresAt?.epochSecond ?: Instant.now().epochSecond,
                        channelId = status.channelId,
                        channelName = status.channelName,
                    ),
                )
            }
            task?.cancel()
            if (saveResult.isSuccess) {
                sender.notify("${platform} 인증이 완료되었습니다.")
                onTokenSaved(minecraftUuid, sender.name, status.platform ?: platform)
            } else {
                sender.notify("토큰 저장에 실패했습니다. 콘솔 로그를 확인해 주세요.")
            }
        }
    }

    private fun userMessage(throwable: Throwable): String {
        val code = (throwable as? AuthClientException)?.code
        return when (code) {
            "INVALID_SHARED_SECRET" -> "AuthServer sharedSecret이 일치하지 않습니다."
            "AUTH_SERVER_UNREACHABLE" -> "AuthServer에 연결할 수 없습니다."
            else -> "AuthServer 인증 시작에 실패했습니다."
        }
    }

    companion object {
        private const val TICKS_PER_SECOND = 20L
    }
}

sealed class PairingStartResult {
    data class Started(
        val pairingCode: String,
        val authorizeUrl: String,
    ) : PairingStartResult()

    data class Failure(
        val message: String,
    ) : PairingStartResult()
}
