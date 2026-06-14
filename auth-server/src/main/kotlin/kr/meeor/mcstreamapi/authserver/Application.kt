package kr.meeor.mcstreamapi.authserver

import kr.meeor.mcstreamapi.authserver.cli.CliParser
import kr.meeor.mcstreamapi.authserver.config.ConfigException
import kr.meeor.mcstreamapi.authserver.config.ConfigLoader
import kr.meeor.mcstreamapi.authserver.config.ConfigValidator
import kr.meeor.mcstreamapi.authserver.config.ValidatedConfig
import kr.meeor.mcstreamapi.authserver.http.installExceptionHandling
import kr.meeor.mcstreamapi.authserver.http.installRequestId
import kr.meeor.mcstreamapi.authserver.http.requestId
import kr.meeor.mcstreamapi.authserver.oauth.ChzzkOAuthProvider
import kr.meeor.mcstreamapi.authserver.oauth.InMemoryStateStore
import kr.meeor.mcstreamapi.authserver.oauth.OAuthProviderRegistry
import kr.meeor.mcstreamapi.authserver.oauth.OAuthStateService
import kr.meeor.mcstreamapi.authserver.oauth.SoopOAuthProvider
import kr.meeor.mcstreamapi.authserver.ops.CleanupJob
import kr.meeor.mcstreamapi.authserver.pairing.InMemoryPairingStore
import kr.meeor.mcstreamapi.authserver.pairing.PairingService
import kr.meeor.mcstreamapi.authserver.route.notFoundRoute
import kr.meeor.mcstreamapi.authserver.route.oauthRoutes
import kr.meeor.mcstreamapi.authserver.route.pairingRoutes
import kr.meeor.mcstreamapi.authserver.route.systemRoutes
import kr.meeor.mcstreamapi.authserver.security.LogMasker
import kr.meeor.mcstreamapi.authserver.security.SharedSecretValidator
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

private val appLog = LoggerFactory.getLogger("kr.meeor.mcstreamapi.authserver.Application")

fun main(args: Array<String>) {
    val options = try {
        CliParser.parse(args)
    } catch (exception: IllegalArgumentException) {
        System.err.println("[${BuildInfo.SERVICE_NAME}] ${exception.message}")
        System.err.println(CliParser.usage())
        exitProcess(1)
    }

    if (options.help) {
        println(CliParser.usage())
        return
    }

    if (options.version) {
        println("${BuildInfo.SERVICE_NAME} ${BuildInfo.VERSION}")
        return
    }

    val loader = ConfigLoader()
    val configExists = try {
        loader.ensureConfigExists(options.configPath)
    } catch (exception: Exception) {
        System.err.println("[${BuildInfo.SERVICE_NAME}] Failed to create config.yml. cause=${exception.message}")
        exitProcess(1)
    }

    if (!configExists) {
        println("[${BuildInfo.SERVICE_NAME}] config.yml 파일이 없어 기본값으로 새로 만들었습니다.")
        println("[${BuildInfo.SERVICE_NAME}] publicBaseUrl, sharedSecret, clientId, clientSecret, redirectUri를 설정해주세요.")
        println("[${BuildInfo.SERVICE_NAME}] 설정 완료 후 서버를 다시 실행해주세요.")
        exitProcess(1)
    }

    val validated = try {
        val config = loader.load(options.configPath)
        val validation = ConfigValidator().validate(config)

        validation.disabledPlatforms.forEach { (platform, reason) ->
            println("[${BuildInfo.SERVICE_NAME}] ${platform.displayName()} 설정이 완료되지 않아 $platform OAuth가 비활성화되었습니다. reason=$reason")
        }

        if (!validation.isValid) {
            validation.errors.forEach { error ->
                System.err.println("[${BuildInfo.SERVICE_NAME}] Config validation failed. error=$error")
            }
            exitProcess(1)
        }

        ValidatedConfig(
            config = config,
            enabledPlatforms = validation.enabledPlatforms,
            disabledPlatforms = validation.disabledPlatforms,
        )
    } catch (exception: ConfigException) {
        System.err.println("[${BuildInfo.SERVICE_NAME}] Config load failed. error=${exception.code} message=${exception.message}")
        exitProcess(1)
    } catch (exception: Exception) {
        System.err.println("[${BuildInfo.SERVICE_NAME}] Config load failed. error=CONFIG_LOAD_FAILED message=${exception.message}")
        exitProcess(1)
    }

    if (options.checkConfig) {
        println("[${BuildInfo.SERVICE_NAME}] Config validation succeeded. enabledPlatforms=${validated.enabledPlatforms.joinToString(",")}")
        return
    }

    startServer(validated)
}

private fun startServer(validated: ValidatedConfig) {
    val server = validated.config.server
    val http = validated.config.http

    appLog.info(
        "{} starting host={} port={} enabledPlatforms={}",
        BuildInfo.SERVICE_NAME,
        server.host,
        server.port,
        validated.enabledPlatforms.joinToString(","),
    )

    val pairingService = PairingService(
        store = InMemoryPairingStore(),
        pairingExpireSeconds = validated.config.security.pairingExpireSeconds,
    )
    val stateService = OAuthStateService(
        store = InMemoryStateStore(),
        stateExpireSeconds = validated.config.security.stateExpireSeconds,
    )
    val providerRegistry = OAuthProviderRegistry(
        buildList {
            validated.config.platforms["chzzk"]
                ?.takeIf { "chzzk" in validated.enabledPlatforms }
                ?.let { add(ChzzkOAuthProvider(it)) }
            validated.config.platforms["soop"]
                ?.takeIf { "soop" in validated.enabledPlatforms }
                ?.let { add(SoopOAuthProvider(it)) }
        },
    )
    val sharedSecretValidator = SharedSecretValidator(validated.config.security.sharedSecret)
    val cleanupJob = CleanupJob(validated.config.cleanup, pairingService, stateService)
    val stopped = AtomicBoolean(false)

    val engine = embeddedServer(
        Netty,
        configure = {
            connector {
                host = server.host
                port = server.port
            }
            requestReadTimeoutSeconds = http.requestTimeoutSeconds.toInt()
            responseWriteTimeoutSeconds = http.requestTimeoutSeconds.toInt()
        },
    ) {
        val logMasker = LogMasker()

        install(ContentNegotiation) {
            json()
        }
        installRequestId()
        install(CallLogging) {
            level = Level.INFO
            filter { true }
            format { call ->
                val status = call.response.status()?.value?.toString() ?: "-"
                "HTTP ${call.request.httpMethod.value} ${call.request.path()} status=$status requestId=${call.requestId()} remote=${call.request.origin.remoteHost}"
            }
        }
        installExceptionHandling(logMasker)

        routing {
            systemRoutes(validated)
            pairingRoutes(validated, pairingService, sharedSecretValidator)
            oauthRoutes(validated, pairingService, stateService, providerRegistry)
            notFoundRoute()
        }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            if (stopped.compareAndSet(false, true)) {
                appLog.info("{} shutdown requested", BuildInfo.SERVICE_NAME)
                cleanupJob.stop()
                engine.stop(http.shutdownTimeoutSeconds * 1000, http.shutdownTimeoutSeconds * 1000)
                appLog.info("{} stopped", BuildInfo.SERVICE_NAME)
            }
        },
    )

    cleanupJob.start()
    engine.start(wait = true)

    if (stopped.compareAndSet(false, true)) {
        cleanupJob.stop()
    }
}

private fun String.displayName(): String =
    when (lowercase()) {
        "chzzk" -> "Chzzk"
        "soop" -> "SOOP"
        else -> this
    }
