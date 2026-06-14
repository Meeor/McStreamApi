package kr.meeor.mcstreamapi.config

data class PluginRuntimeState(
    val createdFiles: List<String>,
    val validation: ConfigValidationResult,
) {
    val firstRun: Boolean = createdFiles.isNotEmpty()
    val runtimeAvailable: Boolean = !firstRun &&
        validation.authAvailable &&
        validation.enabledPlatforms.isNotEmpty()
}
