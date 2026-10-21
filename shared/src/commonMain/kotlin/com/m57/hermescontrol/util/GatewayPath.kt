package com.m57.hermescontrol.util

/**
 * Path handling for gateway media requests, shared by the chat UI and the file client.
 * Moved out of `GatewayFileClient` so `:shared` can use it; the JVM `Pattern` is a
 * [Regex] here, the same pattern compiled once instead of on every call.
 */
object GatewayPath {
    /** Windows drive-absolute (`C:\` or `C:/`), which the gateway namespace rejects. */
    private val WINDOWS_ABSOLUTE = Regex("""^[A-Za-z]:[/\\]""")

    /** Validate in the gateway namespace; preserve `~` for server resolution. */
    fun normalizePath(raw: String): String? {
        val trimmed = raw.trim().removeSurrounding("`").removeSurrounding("\"").removeSurrounding("'")
        if (trimmed == "~" || trimmed.startsWith("~/")) return trimmed
        if (!trimmed.startsWith("/") && !WINDOWS_ABSOLUTE.containsMatchIn(trimmed)) return null
        return trimmed
    }
}
