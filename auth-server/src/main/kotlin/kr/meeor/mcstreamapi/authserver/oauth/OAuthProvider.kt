package kr.meeor.mcstreamapi.authserver.oauth

import kr.meeor.mcstreamapi.authserver.pairing.ChannelInfo
import kr.meeor.mcstreamapi.authserver.pairing.OAuthToken

interface OAuthProvider {
    val platform: String
    val supportsState: Boolean
        get() = true

    fun buildAuthorizeUrl(state: String): String

    suspend fun exchangeCodeForToken(code: String, state: String): OAuthToken

    suspend fun fetchChannelInfo(accessToken: String): ChannelInfo
}

class OAuthProviderRegistry(
    providers: Collection<OAuthProvider>,
) {
    private val providersByPlatform = providers.associateBy { it.platform.lowercase() }

    fun find(platform: String): OAuthProvider? =
        providersByPlatform[platform.lowercase()]
}

class ProviderNotImplementedException(platform: String) :
    RuntimeException("OAuth provider is not implemented yet. platform=$platform")

class OAuthProviderException(
    val errorCode: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
