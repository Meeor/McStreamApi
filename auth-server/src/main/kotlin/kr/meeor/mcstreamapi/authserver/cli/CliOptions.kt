package kr.meeor.mcstreamapi.authserver.cli

import java.nio.file.Path
import kotlin.io.path.Path

data class CliOptions(
    val configPath: Path = Path("config.yml"),
    val checkConfig: Boolean = false,
    val version: Boolean = false,
    val help: Boolean = false,
)

object CliParser {
    fun parse(args: Array<String>): CliOptions {
        var configPath = Path("config.yml")
        var checkConfig = false
        var version = false
        var help = false

        var index = 0
        while (index < args.size) {
            when (val arg = args[index]) {
                "--config" -> {
                    val value = args.getOrNull(index + 1)
                        ?: throw IllegalArgumentException("--config requires a path.")
                    configPath = Path(value)
                    index += 2
                }

                "--check-config" -> {
                    checkConfig = true
                    index += 1
                }

                "--version" -> {
                    version = true
                    index += 1
                }

                "--help", "-h" -> {
                    help = true
                    index += 1
                }

                else -> throw IllegalArgumentException("Unknown argument: $arg")
            }
        }

        return CliOptions(
            configPath = configPath,
            checkConfig = checkConfig,
            version = version,
            help = help,
        )
    }

    fun usage(): String = """
        McStreamApi AuthServer

        Usage:
          java -jar McStreamApi-AuthServer.jar [options]

        Options:
          --config <path>   Use a specific config.yml path.
          --check-config    Validate config and exit without starting the server.
          --version         Print version and exit.
          --help, -h        Print this help.
    """.trimIndent()
}
