package com.m57.hermescontrol.ui.chat

// What installing a session does to the screen state, as rules instead of inline `copy` chains.
//
// Two branches of `handleRpcResult` — session create and session branch — performed the same reset:
// a new id, ready, not loading, an empty transcript, no subagent chips, no todos, a default title.
// The branch case additionally took the child's title, model and context usage from the response.
//
// Extracted because the two copies differed in ways nobody wrote down: create cleared the composer's
// model label, branch *set* one; create left `contextUsage` alone, branch replaced it — even when the
// response carried none, because a branch must never inherit the parent's gauge.

/**
 * @param replaceContextUsage true for a branch: the parent's gauge must not survive into a child that
 *   reported no usage of its own. A create leaves whatever the screen had.
 */
fun TranscriptState.withInstalledSession(
    storageId: String,
    title: String = "Hermes",
    contextUsage: ContextUsage? = null,
    replaceContextUsage: Boolean = false,
): TranscriptState =
    copy(
        currentSessionId = storageId,
        isSessionReady = true,
        isLoading = false,
        messages = emptyList(),
        subagentIndicators = emptyList(),
        todos = emptyList(),
        chatTitle = title,
        contextUsage = if (replaceContextUsage) contextUsage else this.contextUsage,
    )

/** A freshly created session has no model label or terminal backend until one is reported. */
fun ComposerState.withNoSessionModel(): ComposerState = copy(currentSessionModel = null, terminalBackend = null)

/**
 * A branched session reports its own model, reasoning effort and terminal backend. Blank values mean
 * "not reported", which is different from "empty".
 */
fun ComposerState.withSessionModel(
    provider: String,
    model: String,
    reasoningLevel: String?,
    terminalBackend: String?,
): ComposerState =
    copy(
        currentSessionModel = model.takeIf { it.isNotEmpty() }?.let { qualifyModelLabel(provider, it) },
        reasoningLevel = reasoningLevel?.takeIf { it.isNotEmpty() },
        terminalBackend = terminalBackend?.takeIf { it.isNotEmpty() },
    )
