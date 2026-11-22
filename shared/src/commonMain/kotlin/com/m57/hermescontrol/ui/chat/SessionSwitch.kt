package com.m57.hermescontrol.ui.chat

/**
 * What the screen looks like the instant a different session is selected.
 *
 * Every field here is a deliberate reset, and each one was learned the hard way:
 *  - `isAgentTyping` is per-session occupancy; carrying the previous session's fill over misreports it until
 *    the first turn lands;
 *  - `todos` and `contextUsage` belong to the conversation that just closed;
 *  - `currentSessionModel` is a per-session choice, so the old one must not appear to follow the user;
 *  - the session picker and the model-confirmation overlay are modal state about the *old* session.
 *
 * The one thing that is *not* reset is `isLoading`: it is left exactly as it was, because clearing it here
 * raced the fetch completion and left freshly switched sessions stuck on an empty transcript.
 *
 * Painting from the transcript cache is deliberate too: on web there is no Room, so blanking made every
 * switch a visible flash. A cache miss legitimately starts empty.
 */
fun switchedState(
    state: ChatUiState,
    sessionId: String,
    cachedMessages: List<ChatMessage>,
    title: String,
): ChatUiState =
    state.copy(
        transcript =
            state.transcript.copy(
                isSessionReady = false,
                isLoadingOlder = false,
                hasOlderMessages = false,
                currentSessionId = sessionId,
                messages = cachedMessages,
                subagentIndicators = emptyList(),
                todos = emptyList(),
                chatTitle = title,
                isAgentTyping = false,
                contextUsage = null,
            ),
        rail = state.rail.copy(showSessionPicker = false),
        composer =
            state.composer.copy(
                currentSessionModel = null,
                terminalBackend = null,
                pendingPrefillText = null,
            ),
        overlays = state.overlays.copy(modelSwitchConfirmation = null, showContextDetail = false),
    )
