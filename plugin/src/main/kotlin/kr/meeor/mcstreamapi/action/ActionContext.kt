package kr.meeor.mcstreamapi.action

import kr.meeor.mcstreamapi.placeholder.PlaceholderContext
import kr.meeor.mcstreamapi.placeholder.PlaceholderEventState

data class ActionContext(
    val playerName: String,
    val rewardId: String,
    val placeholderContext: PlaceholderContext? = null,
    val placeholderEventState: PlaceholderEventState = PlaceholderEventState(),
    val customItems: Map<String, Map<String, Any?>> = emptyMap(),
)
