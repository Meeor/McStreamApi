package kr.meeor.mcstreamapi.action

data class ActionExecutionResult(
    val actionType: String,
    val success: Boolean,
    val message: String,
) {
    companion object {
        fun success(actionType: String): ActionExecutionResult {
            return ActionExecutionResult(actionType = actionType, success = true, message = "OK")
        }

        fun failure(actionType: String, message: String): ActionExecutionResult {
            return ActionExecutionResult(actionType = actionType, success = false, message = message)
        }
    }
}
