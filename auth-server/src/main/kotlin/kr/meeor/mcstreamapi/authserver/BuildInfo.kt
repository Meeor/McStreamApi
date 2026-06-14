package kr.meeor.mcstreamapi.authserver

object BuildInfo {
    const val SERVICE_NAME = "McStreamApi-AuthServer"
    val VERSION: String = BuildInfo::class.java.`package`.implementationVersion ?: "0.1.0-SNAPSHOT"
}
