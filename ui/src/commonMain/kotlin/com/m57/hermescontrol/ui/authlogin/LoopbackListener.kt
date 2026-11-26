package com.m57.hermescontrol.ui.authlogin

/**
 * The redirect parameters the gateway hands back to the loopback listener.
 *
 * [state] is returned so the caller can compare it; it is not trusted here. The listener
 * refuses a mismatched state and keeps waiting, so a stray request cannot end the flow.
 */
data class LoopbackCallback(
    val code: String,
    val state: String?,
)

sealed interface LoopbackOutcome {
    data class Received(
        val callback: LoopbackCallback,
    ) : LoopbackOutcome

    /** Nobody called back in time. The browser tab was closed, or the provider was abandoned. */
    data object TimedOut : LoopbackOutcome

    /** The provider redirected with `error` instead of `code`, or without either. */
    data class Rejected(
        val reason: String,
    ) : LoopbackOutcome
}

/**
 * A one-shot HTTP listener on `127.0.0.1` that receives the OAuth redirect.
 *
 * The gateway only accepts a loopback redirect (RFC 8252 §8.3), which is exactly what makes the
 * native flow safe: no other machine can be handed the code. That also means the listener must
 * live on the device running the browser, which is why the web build cannot use this at all.
 */
interface LoopbackListener {
    /** The bound port. Put it in the redirect URI before the browser is opened. */
    val port: Int

    /**
     * Waits for one acceptable callback.
     *
     * Requests arriving with the wrong `state` are answered `400` and ignored: a redirect from a
     * different flow must not abort this one.
     */
    suspend fun awaitCallback(expectedState: String): LoopbackOutcome

    /** Releases the port. Safe to call more than once. */
    fun close()
}

/**
 * Opens the listener on an ephemeral loopback port.
 *
 * Not supported on wasmJs — a browser cannot listen, which is the whole reason the web build
 * keeps the cookie flow.
 */
expect fun openLoopbackListener(timeoutMillis: Long = DEFAULT_LOOPBACK_TIMEOUT_MILLIS): LoopbackListener

/** Long enough for a provider login with a 2FA prompt, short enough to release the port. */
const val DEFAULT_LOOPBACK_TIMEOUT_MILLIS: Long = 60_000
