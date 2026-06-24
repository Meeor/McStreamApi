package kr.meeor.mcstreamapi.reward

import kr.meeor.mcstreamapi.action.ActionContext
import kr.meeor.mcstreamapi.action.ActionExecutor
import kr.meeor.mcstreamapi.action.ActionParser
import kr.meeor.mcstreamapi.action.CustomItemConfigLoader
import kr.meeor.mcstreamapi.donation.DonationEvent
import kr.meeor.mcstreamapi.logging.PluginLogger
import kr.meeor.mcstreamapi.placeholder.PlaceholderContext
import java.nio.file.Path

class ManualRewardApplier(
    private val apiConfigPath: Path,
    private val streamerRewardConfigPath: Path? = null,
    private val customItemConfigPath: Path? = null,
    private val apiRewardConfigLoader: ApiRewardConfigLoader = ApiRewardConfigLoader(),
    private val streamerRewardConfigLoader: StreamerRewardConfigLoader = StreamerRewardConfigLoader(),
    private val customItemConfigLoader: CustomItemConfigLoader = CustomItemConfigLoader(),
    private val rewardSelector: RewardSelector = RewardSelector(),
    private val actionExecutor: ActionExecutor,
    private val logger: PluginLogger? = null,
    private val playerUuidResolver: (String) -> String? = { null },
    private val streamerRewardsEnabled: () -> Boolean = { false },
) {
    fun amountSuggestions(): List<String> {
        return runCatching { apiRewardConfigLoader.load(apiConfigPath).amountSuggestions() }
            .getOrElse { throwable ->
                logger?.error("API_CONFIG_LOAD_FAILED path=$apiConfigPath", throwable)
                emptyList()
            }
    }

    fun streamerAmountSuggestions(playerUuid: String, platform: String? = null): List<String> {
        if (!streamerRewardsEnabled()) {
            return emptyList()
        }
        return runCatching {
            val config = streamerRewardConfigPath?.let(streamerRewardConfigLoader::load)
                ?: return@runCatching emptyList()
            val platforms = platform?.let { listOf(it.lowercase()) } ?: config.platforms(playerUuid)
            platforms.flatMap { config.rewards(playerUuid, it) }
                .mapNotNull { it["amount"]?.toString()?.trim() }
                .filter { it.toLongOrNull()?.let { amount -> amount > 0 } == true }
                .distinct()
                .sortedBy(String::toLong)
        }.getOrElse { emptyList() }
    }

    fun apply(playerName: String, amount: Long, platform: String? = null): ManualRewardApplyResult {
        return applyReward(
            playerName = playerName,
            playerUuid = playerUuidResolver(playerName).orEmpty(),
            streamerName = playerName,
            platformFilter = platform,
            donatorName = "manual",
            amount = amount,
            message = "manual apply",
        )
    }

    fun applyDonation(playerName: String, playerUuid: String, event: DonationEvent): ManualRewardApplyResult {
        return applyReward(
            playerName = playerName,
            playerUuid = playerUuid,
            streamerName = event.streamerName,
            platformFilter = event.platform,
            donatorName = event.donatorName,
            amount = event.amount,
            message = event.message,
        )
    }

    fun applyStreamer(
        playerName: String,
        playerUuid: String,
        amount: Long,
        platform: String,
    ): ManualRewardApplyResult {
        if (!streamerRewardsEnabled()) {
            return ManualRewardApplyResult.Failure("스트리머 전용 보상 기능이 비활성화되어 있습니다.")
        }
        return applyReward(
            playerName = playerName,
            playerUuid = playerUuid,
            streamerName = playerName,
            platformFilter = platform,
            donatorName = "manual-streamer",
            amount = amount,
            message = "manual streamer apply",
            streamerOnly = true,
        )
    }

    private fun applyReward(
        playerName: String,
        playerUuid: String,
        streamerName: String,
        platformFilter: String?,
        donatorName: String,
        amount: Long,
        message: String?,
        streamerOnly: Boolean = false,
    ): ManualRewardApplyResult {
        val config = if (streamerOnly) {
            ApiRewardConfig(emptyMap())
        } else {
            runCatching { apiRewardConfigLoader.load(apiConfigPath) }
                .getOrElse { throwable ->
                    logger?.error("API_CONFIG_LOAD_FAILED path=$apiConfigPath", throwable)
                    return ManualRewardApplyResult.Failure("Api.yml을 읽을 수 없습니다. 콘솔 로그를 확인해주세요.")
                }
        }
        val streamerConfig = if (streamerRewardsEnabled()) {
            runCatching {
                streamerRewardConfigPath?.let(streamerRewardConfigLoader::load)
                    ?: StreamerRewardConfig(emptyMap())
            }.getOrElse { throwable ->
                logger?.error("STREAMER_REWARD_CONFIG_LOAD_FAILED path=$streamerRewardConfigPath", throwable)
                StreamerRewardConfig(emptyMap())
            }
        } else {
            StreamerRewardConfig(emptyMap())
        }
        val customItems = runCatching {
            customItemConfigPath?.let { customItemConfigLoader.load(it).items }.orEmpty()
        }.getOrElse { throwable ->
            logger?.error("CUSTOM_ITEM_CONFIG_LOAD_FAILED path=$customItemConfigPath", throwable)
            return ManualRewardApplyResult.Failure("custom-item.yml을 읽을 수 없습니다. 콘솔 로그를 확인해주세요.")
        }
        val actionParser = ActionParser(customItems)
        val platforms = platformFilter?.let { listOf(it.lowercase()) }
            ?: (config.rewardsByPlatform.keys + streamerConfig.platforms(playerUuid)).distinct()
        for (platform in platforms) {
            val streamerRewards = streamerConfig.rewards(playerUuid, platform)
            val defaultRewards = if (streamerOnly) emptyList() else config.rewardsByPlatform[platform].orEmpty()
            val selections = rewardSelector.selectAll(
                platform = platform,
                amount = amount,
                streamerRewards = streamerRewards,
                defaultRewards = defaultRewards,
            ) { source, disabled ->
                logger?.warning(
                    "REWARD_DISABLED source=${source.name.lowercase()} platform=$platform " +
                    "rewardId=${disabled.id} reason=${disabled.reason}",
                )
            }
            if (selections.isEmpty()) {
                logger?.debug(
                    "§e[대기] 후원 reward 매칭 실패: platform=$platform player=$playerName " +
                        "donator=$donatorName amount=$amount streamerRewards=${streamerRewards.size} " +
                        "defaultRewards=${defaultRewards.size}",
                )
                continue
            }

            val rewardIds = mutableListOf<String>()
            var actionCount = 0
            var failedCount = 0
            for (selection in selections) {
                val reward = selection.reward
                val source = selection.source.name.lowercase()
                val parsedActions = actionParser.parse(reward.actions)
                parsedActions.disabledActions.forEach { disabled ->
                    logger?.warning(
                        "ACTION_DISABLED source=$source platform=$platform rewardId=${reward.id} " +
                            "actionIndex=${disabled.index} actionType=${disabled.type} reason=${disabled.reason}",
                    )
                }
                if (parsedActions.actions.isEmpty()) {
                    logger?.warning("REWARD_NO_EXECUTABLE_ACTION source=$source platform=$platform rewardId=${reward.id}")
                    return ManualRewardApplyResult.Failure("선택된 reward에 실행 가능한 action이 없습니다. reward=${reward.id}")
                }

                val context = ActionContext(
                    playerName = playerName,
                    rewardId = reward.id,
                    placeholderContext = PlaceholderContext(
                        playerName = playerName,
                        playerUuid = playerUuid,
                        streamerName = streamerName,
                        platform = platform,
                        donatorName = donatorName,
                        amount = amount,
                        unitCount = reward.unitCount(amount),
                        message = message,
                        rewardId = reward.id,
                    ),
                    customItems = customItems,
                )
                val results = actionExecutor.execute(context, parsedActions.actions)
                val failed = results.filterNot { it.success }
                failed.forEachIndexed { index, result ->
                    logger?.warning(
                        "ACTION_EXECUTION_FAILED source=$source platform=$platform rewardId=${reward.id} " +
                            "actionIndex=$index actionType=${result.actionType} reason=${result.message}",
                    )
                }
                rewardIds.add(reward.id)
                actionCount += results.size
                failedCount += failed.size
            }
            val rewardId = rewardIds.joinToString(",")
            return if (failedCount == 0) {
                ManualRewardApplyResult.Success(rewardId, platform, actionCount)
            } else {
                ManualRewardApplyResult.PartialSuccess(rewardId, platform, actionCount, failedCount)
            }
        }

        return if (streamerOnly) {
            ManualRewardApplyResult.Failure("금액 ${amount}에 맞는 스트리머 전용 reward가 없습니다.")
        } else {
            ManualRewardApplyResult.Failure("금액 ${amount}에 맞는 reward가 없습니다.")
        }
    }

}

sealed class ManualRewardApplyResult {
    data class Success(
        val rewardId: String,
        val platform: String,
        val actionCount: Int,
    ) : ManualRewardApplyResult()

    data class PartialSuccess(
        val rewardId: String,
        val platform: String,
        val actionCount: Int,
        val failedCount: Int,
    ) : ManualRewardApplyResult()

    data class Failure(
        val message: String,
    ) : ManualRewardApplyResult()
}
