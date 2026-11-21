package com.m57.hermescontrol.ui.chat

// The send pipeline's decisions, as data.
//
// `sendMessage` is 183 lines: guards, slash routing, an optimistic append, an attachment upload loop and
// a redirect-vs-submit choice. Only two parts of that are decisions rather than IO, and they were the
// parts nobody could test without a ViewModel, a socket and a session:
//
//  1. may this text be sent at all, and by which route (planSend);
//  2. given a running turn, is this a new prompt or a steer (chooseDelivery).
//
// Everything else — reading content URIs, awaiting attach RPCs, persistence, generations — stays in the
// ViewModel, where the effects live.

/** What a call to send should do, decided before any IO. */
sealed interface SendStart {
    /** Not ready, empty, or unroutable: the caller returns false and nothing is sent. */
    data object Refused : SendStart

    /** A bare `/model` opens the picker instead of dispatching (issue #589). */
    data object OpenModelPicker : SendStart

    /** Any other slash command is dispatched, never submitted as a prompt. */
    data class DispatchCommand(
        val command: String,
    ) : SendStart

    /** A real prompt: snapshot attachments, append optimistically, upload, then deliver. */
    data object Prepare : SendStart
}

/**
 * True for `/model` and `/model   ` — a bare command opens the picker. `/model gpt-5` does not: an
 * argument means the user already chose, so it dispatches normally.
 */
fun isBareModelCommand(command: String): Boolean {
    val trimmed = command.trim()
    if (!trimmed.startsWith("/", ignoreCase = true)) return false
    val body = trimmed.removePrefix("/").trimStart()
    return body.equals("model", ignoreCase = true) ||
        (body.startsWith("model ", ignoreCase = true) && body.substringAfter("model").trim().isEmpty())
}

/**
 * The guard chain, in one place and in the order it must run: readiness first (a send into a session
 * that is not installed cannot be routed), then emptiness, then routability, then slash routing.
 */
fun planSend(
    text: String,
    hasAttachments: Boolean,
    sessionReady: Boolean,
    hasStorageSessionId: Boolean,
    hasRuntimeSessionId: Boolean,
    hasConnection: Boolean,
): SendStart {
    if (!sessionReady) return SendStart.Refused
    if (text.isBlank() && !hasAttachments) return SendStart.Refused
    if (!hasStorageSessionId || !hasRuntimeSessionId || !hasConnection) return SendStart.Refused

    val trimmed = text.trim()
    if (trimmed.startsWith("/", ignoreCase = true)) {
        return if (isBareModelCommand(trimmed)) {
            SendStart.OpenModelPicker
        } else {
            SendStart.DispatchCommand(trimmed)
        }
    }
    return SendStart.Prepare
}

/** How a prepared prompt reaches the backend. */
enum class Delivery {
    /** A turn is running and the text carries no attachments: steer it. */
    Redirect,

    /** Otherwise submit a new prompt. */
    Submit,
}

/**
 * `session.redirect` is text-only, so an attachment send always submits — the attachment would be
 * silently dropped otherwise.
 */
fun chooseDelivery(
    wasStreaming: Boolean,
    hasAttachments: Boolean,
): Delivery = if (wasStreaming && !hasAttachments) Delivery.Redirect else Delivery.Submit
