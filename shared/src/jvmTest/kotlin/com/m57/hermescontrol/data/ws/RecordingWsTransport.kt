package com.m57.hermescontrol.data.ws

import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [WsTransport] for tests: records what the client asked for and hands
 * back a socket it can inspect. No platform, no I/O.
 *
 * The real implementations are `OkHttpWsTransport` in `:app` and — for a future
 * web target — a Ktor one. This fake is what lets the client be exercised
 * anywhere, which is the point of the seam.
 */
class RecordingWsTransport : WsTransport {
    /** Completes with the first URL the client connects to, on any dispatcher. */
    val firstConnect = CompletableDeferred<String>()

    val connectedUrls = mutableListOf<String>()
    val socket = RecordingWsSocket()
    var listener: WsListener? = null

    override fun connect(
        url: String,
        listener: WsListener,
    ): WsSocket {
        connectedUrls += url
        this.listener = listener
        if (!firstConnect.isCompleted) firstConnect.complete(url)
        return socket
    }
}

/** A [WsSocket] that remembers what it was asked to do. */
class RecordingWsSocket : WsSocket {
    val sent = mutableListOf<String>()
    var closed: Pair<Int, String>? = null
    var cancelled = false

    override fun send(text: String): Boolean {
        sent += text
        return true
    }

    override fun close(
        code: Int,
        reason: String,
    ): Boolean {
        closed = code to reason
        return true
    }

    override fun cancel() {
        cancelled = true
    }
}
