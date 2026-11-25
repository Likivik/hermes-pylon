package com.m57.hermescontrol.ui.chat

/**
 * The state a new session starts from.
 *
 * `createNewSession` used to open-code these resets inside a 40-line method that also fenced generations and
 * dispatched the request. The resets are the part with rules — what must be cleared so the previous session cannot
 * leak into the new one — so they are named here and tested; the method keeps only the ordering it owns.
 *
 * **Cleared:** everything that describes the old conversation — messages, todos, subagent rows, context usage,
 * session id, readiness, title — plus the per-session model and terminal backend, because they belonged to that
 * session.
 *
 * **Kept on purpose:** attachments, slash-command history, reasoning level and typing preferences. Those describe
 * what the user is doing, not which session they are doing it in, and throwing them away on "new session" would
 * lose work.
 */
fun ChatUiState.afterNewSession(setLoading: Boolean): ChatUiState =
    copy(
        transcript =
            transcript.copy(
                isLoading = setLoading,
                currentSessionId = null,
                isSessionReady = false,
                messages = emptyList(),
                subagentIndicators = emptyList(),
                todos = emptyList(),
                chatTitle = "Hermes",
                contextUsage = null,
            ),
        composer =
            composer.copy(
                currentSessionModel = null,
                terminalBackend = null,
                pendingPrefillText = null,
            ),
        overlays = overlays.copy(showContextDetail = false),
    )
