package kr.meeor.mcstreamapi.session

import kr.meeor.mcstreamapi.donation.DonationEvent

fun interface DonationRewardPipeline {
    fun handle(playerUuid: String, event: DonationEvent)
}
