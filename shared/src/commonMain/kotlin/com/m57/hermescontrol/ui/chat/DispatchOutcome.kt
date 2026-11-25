package com.m57.hermescontrol.ui.chat

/**
 * What a `command.dispatch` result asks the client to do.
 *
 * The gateway answers a slash command with one of five `type` values, and the client's five `when`
 * arms were really three behaviours: submit a prompt, show output, or follow an alias. Two pairs were
 * duplicated (`send`/`skill`, `exec`/`plugin`), and the fallback differed between them — `exec` falls
 * back to `message`, `plugin` does not. That difference is exactly the kind that gets "tidied" away by
 * a later reader unless it is written down.
 */
sealed interface DispatchOutcome {
    /** `send` / `skill`: the backend produced a prompt for the agent. */
    data class SendPrompt(
        val message: String,
    ) : DispatchOutcome

    /** `exec` / `plugin` / unknown: the backend produced output to show. */
    data class ShowOutput(
        val output: String,
    ) : DispatchOutcome

    /** `alias`: dispatch resolved to another command, which must be handled locally. */
    data class FollowAlias(
        val target: String,
    ) : DispatchOutcome
}

/**
 * @return null when the result is not a map or carries no `type`, which the caller treats as "nothing
 *   to do" rather than an error — the request was answered, just with an empty payload.
 */
fun classifyDispatchResult(result: Any?): DispatchOutcome? {
    val map = result as? Map<*, *> ?: return null
    val type = map["type"] as? String ?: return null
    return when (type) {
        "send", "skill" -> DispatchOutcome.SendPrompt(map["message"] as? String ?: "")
        "alias" -> DispatchOutcome.FollowAlias(map["target"] as? String ?: return null)
        "exec" -> DispatchOutcome.ShowOutput(map["output"] as? String ?: map["message"] as? String ?: "")
        // `plugin` deliberately has no message fallback; unknown types echo the whole map.
        "plugin" -> DispatchOutcome.ShowOutput(map["output"] as? String ?: "")
        else -> DispatchOutcome.ShowOutput(map["output"] as? String ?: map.toString())
    }
}
