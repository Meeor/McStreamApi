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
    private val customItemConfigPath: Path? = null,
    private val apiRewardConfigLoader: ApiRewardConfigLoader = ApiRewardConfigLoader(),
    private val customItemConfigLoader: CustomItemConfigLoader = CustomItemConfigLoader(),
    private val rewardParser: RewardParser = RewardParser(),
    private val rewardMatcher: RewardMatcher = RewardMatcher(),
    private val actionExecutor: ActionExecutor,
    private val logger: PluginLogger? = null,
) {
    fun amountSuggestions(): List<String> {
        return runCatching { apiRewardConfigLoader.load(apiConfigPath).amountSuggestions() }
            .getOrElse { throwable ->
                logger?.error("API_CONFIG_LOAD_FAILED path=$apiConfigPath", throwable)
                emptyList()
            }
    }

    fun apply(playerName: String, amount: Long): ManualRewardApplyResult {
        return applyReward(
            playerName = playerName,
            streamerName = playerName,
            platformFilter = null,
            donatorName = "manual",
            amount = amount,
            message = "manual apply",
        )
    }

    fun applyDonation(playerName: String, event: DonationEvent): ManualRewardApplyResult {
        return applyReward(
            playerName = playerName,
            streamerName = event.streamerName,
            platformFilter = event.platform,
            donatorName = event.donatorName,
            amount = event.amount,
            message = event.message,
        )
    }

    private fun applyReward(
        playerName: String,
        streamerName: String,
        platformFilter: String?,
        donatorName: String,
        amount: Long,
        message: String?,
    ): ManualRewardApplyResult {
        val config = runCatching { apiRewardConfigLoader.load(apiConfigPath) }
            .getOrElse { throwable ->
                logger?.error("API_CONFIG_LOAD_FAILED path=$apiConfigPath", throwable)
                return ManualRewardApplyResult.Failure("Api.yml을 읽을 수 없습니다. 콘솔 로그를 확인해주세요.")
            }
        val customItems = runCatching {
            customItemConfigPath?.let { customItemConfigLoader.load(it).items }.orEmpty()
        }.getOrElse { throwable ->
            logger?.error("CUSTOM_ITEM_CONFIG_LOAD_FAILED path=$customItemConfigPath", throwable)
            return ManualRewardApplyResult.Failure("custom-item.yml을 읽을 수 없습니다. 콘솔 로그를 확인해주세요.")
        }
        val actionParser = ActionParser(customItems)
        for ((platform, rawRewards) in config.rewardsByPlatform) {
            if (platformFilter != null && platform != platformFilter) {
                continue
            }
            val parsedRewards = rewardParser.parse(platform, rawRewards)
            parsedRewards.disabledRewards.forEach { disabled ->
                logger?.warning(
                    "REWARD_DISABLED platform=$platform rewardId=${disabled.id} reason=${disabled.reason}",
                )
            }
            val reward = rewardMatcher.match(parsedRewards.rewards, amount) ?: continue
            val parsedActions = actionParser.parse(reward.actions)
            parsedActions.disabledActions.forEach { disabled ->
                logger?.warning(
                    "ACTION_DISABLED platform=$platform rewardId=${reward.id} actionIndex=${disabled.index} actionType=${disabled.type} reason=${disabled.reason}",
                )
            }
            if (parsedActions.actions.isEmpty()) {
                logger?.warning("REWARD_NO_EXECUTABLE_ACTION platform=$platform rewardId=${reward.id}")
                return ManualRewardApplyResult.Failure("선택된 reward에 실행 가능한 action이 없습니다. reward=${reward.id}")
            }

            val context = ActionContext(
                playerName = playerName,
                rewardId = reward.id,
                placeholderContext = PlaceholderContext(
                    playerName = playerName,
                    streamerName = streamerName,
                    platform = platform,
                    donatorName = donatorName,
                    amount = amount,
                    message = message,
                    rewardId = reward.id,
                ),
                customItems = customItems,
            )
            val results = actionExecutor.execute(context, parsedActions.actions)
            val failed = results.filterNot { it.success }
            failed.forEachIndexed { index, result ->
                logger?.warning(
                    "ACTION_EXECUTION_FAILED platform=$platform rewardId=${reward.id} actionIndex=$index actionType=${result.actionType} reason=${result.message}",
                )
            }
            return if (failed.isEmpty()) {
                ManualRewardApplyResult.Success(reward.id, platform, results.size)
            } else {
                ManualRewardApplyResult.PartialSuccess(reward.id, platform, results.size, failed.size)
            }
        }

        return ManualRewardApplyResult.Failure("금액 ${amount}에 맞는 reward가 없습니다.")
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
