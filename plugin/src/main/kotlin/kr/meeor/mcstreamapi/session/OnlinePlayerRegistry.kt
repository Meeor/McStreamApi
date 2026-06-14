package kr.meeor.mcstreamapi.session

interface OnlinePlayerRegistry {
    fun isOnline(playerUuid: String): Boolean

    fun notify(playerUuid: String, message: String)
}
