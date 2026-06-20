package kr.meeor.mcstreamapi

import kr.meeor.mcstreamapi.auth.BukkitPairingScheduler
import kr.meeor.mcstreamapi.action.ActionExecutor
import kr.meeor.mcstreamapi.action.BukkitActionPlatform
import kr.meeor.mcstreamapi.command.McaBukkitCommand
import kr.meeor.mcstreamapi.command.McaCommandService
import kr.meeor.mcstreamapi.command.StreamerPlayerIdentity
import kr.meeor.mcstreamapi.config.PluginConfigBootstrap
import kr.meeor.mcstreamapi.config.PluginRuntimeState
import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.auth.PairingConnector
import kr.meeor.mcstreamapi.donation.DonationProvider
import kr.meeor.mcstreamapi.donation.chzzk.ChzzkDonationProvider
import kr.meeor.mcstreamapi.donation.chzzk.ChzzkSessionApi
import kr.meeor.mcstreamapi.donation.chzzk.ChzzkTokenRefresher
import kr.meeor.mcstreamapi.donation.chzzk.JavaChzzkSessionTransport
import kr.meeor.mcstreamapi.donation.soop.JavaSoopSessionTransport
import kr.meeor.mcstreamapi.donation.soop.SoopDonationProvider
import kr.meeor.mcstreamapi.donation.soop.SoopTokenRefresher
import kr.meeor.mcstreamapi.placeholder.PlaceholderResolver
import kr.meeor.mcstreamapi.placeholder.RandomConfigLoader
import kr.meeor.mcstreamapi.placeholder.RandomResolver
import kr.meeor.mcstreamapi.reward.ManualRewardApplier
import kr.meeor.mcstreamapi.reward.StreamerRewardConfigLoader
import kr.meeor.mcstreamapi.session.BukkitOnlinePlayerRegistry
import kr.meeor.mcstreamapi.session.BukkitPlayerSessionListener
import kr.meeor.mcstreamapi.session.DonationRewardPipeline
import kr.meeor.mcstreamapi.session.PlayerDonationSessionManager
import kr.meeor.mcstreamapi.token.TokenStore
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McStreamApiPlugin : JavaPlugin() {
    private val pluginLogger by lazy {
        PluginLogger(logger) { runtimeState?.validation?.loggingConfig?.debug == true }
    }
    private var runtimeState: PluginRuntimeState? = null
    private var sessionManager: PlayerDonationSessionManager? = null
    private var manualRewardApplier: ManualRewardApplier? = null
    @Volatile
    private var streamerPlayers: List<StreamerPlayerIdentity> = emptyList()
    private val streamerPlayerNames = ConcurrentHashMap<String, String>()

    override fun onEnable() {
        val bootstrap = createConfigBootstrap()
        runtimeState = bootstrap.initialize()

        val state = runtimeState ?: return
        if (state.firstRun) {
            pluginLogger.warning(
                "McStreamApi created default runtime files. Configure config.yml, Api.yml, random.yml, custom-item.yml, then restart. streamer-rewards.yml is created only when streamerRewards.enabled is true.",
            )
            server.pluginManager.disablePlugin(this)
            return
        }

        registerCommands(bootstrap)

        if (!state.runtimeAvailable) {
            pluginLogger.warning("McStreamApi runtime features are disabled until valid auth and platform config exist.")
        }

        registerSessionListener()
        pluginLogger.info("McStreamApi bootstrap loaded. platforms=${state.validation.enabledPlatforms.joinToString(",")}")
    }

    override fun onDisable() {
        sessionManager?.stopAll()
        getCommand("mca")?.setExecutor(null)
        getCommand("mca")?.tabCompleter = null
        pluginLogger.info("McStreamApi bootstrap disabled.")
    }

    private fun registerCommands(bootstrap: PluginConfigBootstrap) {
        val tokenStore = createTokenStore()
        val pairingConnector = PairingConnector(
            tokenStore = tokenStore,
            scheduler = BukkitPairingScheduler(this),
            onTokenSaved = { playerUuid, playerName, platform ->
                server.scheduler.runTask(this, Runnable {
                    sessionManager?.platformAuthenticated(playerUuid, playerName, platform)
                })
            },
        )
        val manualRewardApplier = createManualRewardApplier()
        this.manualRewardApplier = manualRewardApplier
        val commandService = McaCommandService(
            runtimeStateProvider = { runtimeState },
            reloadRuntimeState = { reloadRuntime(bootstrap) },
            pairingConnector = pairingConnector,
            manualRewardApplier = manualRewardApplier,
            connectedPlayerNames = {
                tokenStore.connectedPlayerNames(
                    server.onlinePlayers.associate { it.uniqueId.toString() to it.name },
                )
            },
            connectedTokens = {
                tokenStore.connectedTokens(
                    server.onlinePlayers.associate { it.uniqueId.toString() to it.name },
                )
            },
            activeSessions = { sessionManager?.activeSessions().orEmpty() },
            streamerPlayers = { streamerPlayers },
        )
        val mcaCommand = McaBukkitCommand(this, commandService)
        getCommand("mca")?.setExecutor(mcaCommand)
        getCommand("mca")?.tabCompleter = mcaCommand
        refreshStreamerPlayers()
    }

    private fun createManualRewardApplier(): ManualRewardApplier {
        val randomConfigPath = dataFolder.toPath().resolve("random.yml")
        val randomConfigLoader = RandomConfigLoader()
        return ManualRewardApplier(
            apiConfigPath = dataFolder.toPath().resolve("Api.yml"),
            streamerRewardConfigPath = dataFolder.toPath().resolve("streamer-rewards.yml"),
            customItemConfigPath = dataFolder.toPath().resolve("custom-item.yml"),
            actionExecutor = ActionExecutor(
                platform = BukkitActionPlatform(this, pluginLogger),
                logger = pluginLogger,
                placeholderResolver = PlaceholderResolver(
                    randomResolver = RandomResolver(tableProvider = { randomConfigLoader.load(randomConfigPath) }),
                ),
            ),
            logger = pluginLogger,
            playerUuidResolver = { playerName ->
                server.getPlayerExact(playerName)?.uniqueId?.toString()
            },
            streamerRewardsEnabled = {
                runtimeState?.validation?.streamerRewardsEnabled == true
            },
        )
    }

    private fun registerSessionListener() {
        val tokenStore = createTokenStore()
        sessionManager = PlayerDonationSessionManager(
            tokenStore = tokenStore,
            providers = createDonationProviders(tokenStore),
            onlinePlayers = BukkitOnlinePlayerRegistry(this),
            rewardPipeline = DonationRewardPipeline { playerUuid, event ->
                server.scheduler.runTask(this, Runnable {
                    val player = server.getPlayer(java.util.UUID.fromString(playerUuid)) ?: return@Runnable
                    val result = manualRewardApplier?.applyDonation(player.name, playerUuid, event)
                    pluginLogger.info(
                        "§a[후원] 보상 처리 완료: 플레이어=${player.name} 플랫폼=${event.platform} 후원자=${event.donatorName} " +
                            "수량=${event.amount} 결과=${result?.javaClass?.simpleName ?: "NONE"}",
                    )
                })
            },
            logger = pluginLogger,
        )
        server.pluginManager.registerEvents(BukkitPlayerSessionListener(sessionManager!!), this)
        server.onlinePlayers.forEach { player ->
            sessionManager?.playerJoined(player.uniqueId.toString(), player.name)
        }
    }

    private fun reloadRuntime(bootstrap: PluginConfigBootstrap): PluginRuntimeState {
        val state = bootstrap.initialize()
        runtimeState = state
        refreshStreamerPlayers()
        sessionManager?.replaceProviders(createDonationProviders(createTokenStore()))
        server.onlinePlayers.forEach { player ->
            sessionManager?.playerJoined(player.uniqueId.toString(), player.name)
        }
        return state
    }

    private fun refreshStreamerPlayers() {
        if (runtimeState?.validation?.streamerRewardsEnabled != true) {
            streamerPlayers = emptyList()
            return
        }
        val path = dataFolder.toPath().resolve("streamer-rewards.yml")
        val config = runCatching { StreamerRewardConfigLoader().load(path) }
            .getOrElse { throwable ->
                pluginLogger.error("STREAMER_REWARD_CONFIG_LOAD_FAILED path=$path", throwable)
                streamerPlayers = emptyList()
                return
            }
        streamerPlayers = config.rewardsByPlayerUuid.mapNotNull { (rawUuid, platforms) ->
            val uuid = runCatching { UUID.fromString(rawUuid) }.getOrElse {
                pluginLogger.warning("STREAMER_REWARD_INVALID_UUID uuid=$rawUuid")
                return@mapNotNull null
            }
            StreamerPlayerIdentity(
                uuid = uuid.toString(),
                playerName = server.getPlayer(uuid)?.name ?: streamerPlayerNames[uuid.toString()],
                platforms = platforms.keys,
            )
        }.sortedBy(StreamerPlayerIdentity::displayName)

        streamerPlayers.forEach { identity ->
            val uuid = UUID.fromString(identity.uuid)
            server.createPlayerProfile(uuid).update().whenComplete { profile, throwable ->
                if (throwable != null) {
                    pluginLogger.warning(
                        "STREAMER_PROFILE_LOOKUP_FAILED uuid=${identity.uuid} reason=${throwable.javaClass.simpleName}",
                    )
                    return@whenComplete
                }
                val playerName = profile.name?.takeIf(String::isNotBlank) ?: return@whenComplete
                streamerPlayerNames[identity.uuid] = playerName
                synchronized(this) {
                    streamerPlayers = streamerPlayers.map { current ->
                        if (current.uuid == identity.uuid) current.copy(playerName = playerName) else current
                    }.sortedBy(StreamerPlayerIdentity::displayName)
                }
            }
        }
    }

    private fun createDonationProviders(tokenStore: TokenStore): Map<String, DonationProvider> {
        val state = runtimeState ?: return emptyMap()
        val providers = mutableMapOf<String, DonationProvider>()
        val chzzkConfig = state.validation.platformConfigs["chzzk"]
        if ("chzzk" in state.validation.enabledPlatforms && chzzkConfig != null) {
            providers["chzzk"] = ChzzkDonationProvider(
                tokenRefresher = ChzzkTokenRefresher(
                    clientId = chzzkConfig.clientId,
                    clientSecret = chzzkConfig.clientSecret,
                    refreshBeforeSeconds = chzzkConfig.tokenRefreshBeforeSeconds,
                ),
                tokenStore = tokenStore,
                sessionApi = ChzzkSessionApi(logger = pluginLogger),
                sessionTransport = JavaChzzkSessionTransport(logger = pluginLogger),
                logger = pluginLogger,
            )
        }
        val soopConfig = state.validation.platformConfigs["soop"]
        if ("soop" in state.validation.enabledPlatforms && soopConfig != null) {
            providers["soop"] = SoopDonationProvider(
                tokenRefresher = SoopTokenRefresher(
                    clientId = soopConfig.clientId,
                    clientSecret = soopConfig.clientSecret,
                    refreshBeforeSeconds = soopConfig.tokenRefreshBeforeSeconds,
                ),
                tokenStore = tokenStore,
                sessionTransport = JavaSoopSessionTransport(
                    clientId = soopConfig.clientId,
                    clientSecret = soopConfig.clientSecret,
                    logger = pluginLogger,
                    receiveAdBalloons = soopConfig.receiveAdBalloons,
                    receiveVideoBalloons = soopConfig.receiveVideoBalloons,
                ),
                logger = pluginLogger,
            )
        }
        return providers
    }

    private fun createTokenStore(): TokenStore {
        return TokenStore(
            tokensDirectory = dataFolder.toPath().resolve("tokens"),
            secretKeyPath = dataFolder.toPath().resolve("secret.key"),
        )
    }

    private fun createConfigBootstrap(): PluginConfigBootstrap {
        return PluginConfigBootstrap(
            dataFolder = dataFolder.toPath(),
            resourceProvider = ::getResource,
            logger = pluginLogger,
        )
    }
}
