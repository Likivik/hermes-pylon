package com.m57.hermescontrol.ui

import com.m57.hermescontrol.data.ws.HermesWsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Publishes the WS state to `window.__hermesWsState` for the browser test (plan §1.5).
 *
 * The shape is deliberately tiny and redaction-safe: a status name, a transition counter
 * and the handshake-failure word. No URL, no query string, no ticket — a diagnostic that
 * leaks a credential is worse than no diagnostic.
 */
@JsFun(
    "(status, transitions, failure) => { " +
        "window.__hermesWsState = { status: status, transitions: transitions, lastFailure: failure }; }",
)
private external fun publishWsStateJs(
    status: String,
    transitions: Int,
    failure: String,
)

internal fun CoroutineScope.publishWsState() {
    launch {
        var transitions = 0
        HermesWsClient.connectionStatus.collect { status ->
            transitions += 1
            publishWsStateJs(
                status = status.name,
                transitions = transitions,
                failure = HermesWsClient.lastHandshakeFailure ?: "unknown",
            )
        }
    }
}
