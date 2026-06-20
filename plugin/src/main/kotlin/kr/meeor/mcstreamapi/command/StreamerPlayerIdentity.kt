package kr.meeor.mcstreamapi.command

data class StreamerPlayerIdentity(
    val uuid: String,
    val playerName: String?,
    val platforms: Set<String>,
) {
    val displayName: String = playerName ?: uuid

    fun matches(query: String): Boolean {
        return uuid.equals(query, ignoreCase = true) || playerName.equals(query, ignoreCase = true)
    }
}
