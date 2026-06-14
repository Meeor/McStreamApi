package kr.meeor.mcstreamapi.session

import kr.meeor.mcstreamapi.donation.DonationEvent
import kr.meeor.mcstreamapi.donation.DonationEventListener
import kr.meeor.mcstreamapi.donation.DonationProvider
import kr.meeor.mcstreamapi.donation.DonationProviderSession
import kr.meeor.mcstreamapi.token.OAuthToken
import kr.meeor.mcstreamapi.token.TokenStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerDonationSessionManagerTest {
    @Test
    fun `starts session on join and stops on quit`() {
        val root = Files.createTempDirectory("mcstreamapi-session")
        val tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key"))
        tokenStore.save(token())
        val provider = FakeProvider()
        val online = FakeOnlinePlayers(online = true)
        val manager = PlayerDonationSessionManager(
            tokenStore = tokenStore,
            providers = mapOf("chzzk" to provider),
            onlinePlayers = online,
            rewardPipeline = DonationRewardPipeline { _, _ -> },
        )

        manager.playerJoined(PLAYER_UUID, "Steve")
        manager.playerQuit(PLAYER_UUID)

        assertEquals(1, provider.started)
        assertEquals(1, provider.stopped)
    }

    @Test
    fun `does not forward events for offline players`() {
        val root = Files.createTempDirectory("mcstreamapi-session")
        val tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key"))
        tokenStore.save(token())
        val provider = FakeProvider()
        val online = FakeOnlinePlayers(online = true)
        val handled = mutableListOf<DonationEvent>()
        val manager = PlayerDonationSessionManager(
            tokenStore = tokenStore,
            providers = mapOf("chzzk" to provider),
            onlinePlayers = online,
            rewardPipeline = DonationRewardPipeline { _, event -> handled.add(event) },
        )

        manager.playerJoined(PLAYER_UUID, "Steve")
        online.online = false
        provider.listener?.onDonation(event("event-1"))

        assertEquals(emptyList(), handled)
    }

    @Test
    fun `deduplicates forwarded events`() {
        val root = Files.createTempDirectory("mcstreamapi-session")
        val tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key"))
        tokenStore.save(token())
        val provider = FakeProvider()
        val online = FakeOnlinePlayers(online = true)
        val handled = mutableListOf<DonationEvent>()
        val manager = PlayerDonationSessionManager(
            tokenStore = tokenStore,
            providers = mapOf("chzzk" to provider),
            onlinePlayers = online,
            rewardPipeline = DonationRewardPipeline { _, event -> handled.add(event) },
        )

        manager.playerJoined(PLAYER_UUID, "Steve")
        provider.listener?.onDonation(event("event-1"))
        provider.listener?.onDonation(event("event-1"))

        assertEquals(1, handled.size)
    }

    @Test
    fun `missing token does not start session`() {
        val root = Files.createTempDirectory("mcstreamapi-session")
        val provider = FakeProvider()
        val online = FakeOnlinePlayers(online = true)
        val manager = PlayerDonationSessionManager(
            tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key")),
            providers = mapOf("chzzk" to provider),
            onlinePlayers = online,
            rewardPipeline = DonationRewardPipeline { _, _ -> },
        )

        manager.playerJoined(PLAYER_UUID, "Steve")

        assertEquals(emptyList(), online.messages)
        assertEquals(0, provider.started)
    }

    @Test
    fun `provider failure notifies player to reconnect`() {
        val root = Files.createTempDirectory("mcstreamapi-session")
        val tokenStore = TokenStore(root.resolve("tokens"), root.resolve("secret.key"))
        tokenStore.save(token())
        val online = FakeOnlinePlayers(online = true)
        val manager = PlayerDonationSessionManager(
            tokenStore = tokenStore,
            providers = mapOf("chzzk" to FailingProvider("CHZZK_TOKEN_REFRESH_FAILED")),
            onlinePlayers = online,
            rewardPipeline = DonationRewardPipeline { _, _ -> },
        )

        manager.playerJoined(PLAYER_UUID, "Steve")

        assertEquals(listOf("chzzk 세션을 시작할 수 없습니다. 다시 연결해 주세요."), online.messages)
    }

    private fun token(): OAuthToken {
        return OAuthToken(
            platform = "chzzk",
            minecraftUuid = PLAYER_UUID,
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochSeconds = Long.MAX_VALUE,
            channelId = "channel",
            channelName = "Steve",
        )
    }

    private fun event(eventId: String?): DonationEvent {
        return DonationEvent(
            platform = "chzzk",
            streamerId = "channel",
            streamerName = "Steve",
            donatorName = "donator",
            amount = 1000,
            message = null,
            eventId = eventId,
            occurredAtEpochSeconds = 1,
        )
    }

    private class FakeProvider : DonationProvider {
        override val platform: String = "chzzk"
        var started = 0
        var stopped = 0
        var listener: DonationEventListener? = null

        override fun startSession(
            minecraftUuid: String,
            streamerId: String,
            streamerName: String,
            token: OAuthToken,
            listener: DonationEventListener,
        ): DonationProviderSession {
            started += 1
            this.listener = listener
            return DonationProviderSession { stopped += 1 }
        }
    }

    private class FailingProvider(private val code: String) : DonationProvider {
        override val platform: String = "chzzk"

        override fun startSession(
            minecraftUuid: String,
            streamerId: String,
            streamerName: String,
            token: OAuthToken,
            listener: DonationEventListener,
        ): DonationProviderSession {
            throw FakeProviderException(code)
        }
    }

    private class FakeProviderException(val code: String) : RuntimeException(code)

    private class FakeOnlinePlayers(var online: Boolean) : OnlinePlayerRegistry {
        val messages = mutableListOf<String>()

        override fun isOnline(playerUuid: String): Boolean = online

        override fun notify(playerUuid: String, message: String) {
            messages.add(message)
        }
    }

    private companion object {
        const val PLAYER_UUID = "00000000-0000-0000-0000-000000000001"
    }
}
