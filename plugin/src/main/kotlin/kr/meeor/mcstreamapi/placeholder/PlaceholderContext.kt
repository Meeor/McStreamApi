package kr.meeor.mcstreamapi.placeholder

data class PlaceholderContext(
    val playerName: String,
    val streamerName: String,
    val platform: String,
    val donatorName: String,
    val amount: Long,
    val message: String? = null,
    val rewardId: String,
)
